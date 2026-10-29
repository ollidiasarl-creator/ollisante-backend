package com.ollisante.backend.service;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Statistiques des rendez-vous (date du rendez-vous, en UTC), pour le site d'administration. */
@Service
@RequiredArgsConstructor
public class StatsService {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final EntityManager em;

    public record MonthRow(String month, long total, long confirmed, long completed, long cancelled) {
    }

    public record CountRow(String code, String label, long count) {
    }

    public record AppointmentStats(String from, String to, long total, List<MonthRow> byMonth,
                                   List<CountRow> byStatus, List<CountRow> byCountry, List<CountRow> bySpecialty) {
    }

    @Transactional(readOnly = true)
    public AppointmentStats appointments(int months) {
        int span = Math.min(Math.max(months, 1), 36);
        LocalDate firstMonth = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).minusMonths(span - 1L);
        LocalDate end = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).plusMonths(1);
        var from = firstMonth.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
        var to = end.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();

        // Tous les mois de la période, même sans rendez-vous (courbe continue).
        Map<String, long[]> perMonth = new LinkedHashMap<>();
        for (LocalDate m = firstMonth; m.isBefore(end); m = m.plusMonths(1)) perMonth.put(m.format(MONTH), new long[4]);
        for (Object[] r : rows("""
                select to_char(date_trunc('month', starts_at at time zone 'UTC'), 'YYYY-MM'), status, count(*)
                from appointments where starts_at >= :from and starts_at < :to group by 1, 2""", from, to)) {
            long[] c = perMonth.get((String) r[0]);
            if (c == null) continue;
            long n = ((Number) r[2]).longValue();
            c[0] += n;
            switch ((String) r[1]) {
                case "CONFIRMED", "PENDING_PAYMENT" -> c[1] += n;
                case "COMPLETED" -> c[2] += n;
                case "CANCELLED", "NO_SHOW" -> c[3] += n;
                default -> { }
            }
        }
        List<MonthRow> byMonth = new ArrayList<>();
        perMonth.forEach((m, c) -> byMonth.add(new MonthRow(m, c[0], c[1], c[2], c[3])));

        List<CountRow> byStatus = counts("""
                select status, status, count(*) from appointments
                where starts_at >= :from and starts_at < :to group by status order by 3 desc""", from, to);
        List<CountRow> byCountry = counts("""
                select c.code, c.name_fr, count(*) from appointments a
                join practitioners p on p.id = a.practitioner_id join countries c on c.code = p.country_code
                where a.starts_at >= :from and a.starts_at < :to group by c.code, c.name_fr order by 3 desc""", from, to);
        // Un rendez-vous compte pour chaque spécialité du praticien consulté.
        List<CountRow> bySpecialty = counts("""
                select s.code, s.name_fr, count(*) from appointments a
                join practitioner_specialties ps on ps.practitioner_id = a.practitioner_id
                join specialties s on s.id = ps.specialty_id
                where a.starts_at >= :from and a.starts_at < :to group by s.code, s.name_fr order by 3 desc""", from, to);
        long total = byMonth.stream().mapToLong(MonthRow::total).sum();
        return new AppointmentStats(firstMonth.toString(), end.minusDays(1).toString(), total, byMonth, byStatus,
                byCountry, bySpecialty);
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> rows(String sql, Object from, Object to) {
        return em.createNativeQuery(sql).setParameter("from", from).setParameter("to", to).getResultList();
    }

    private List<CountRow> counts(String sql, Object from, Object to) {
        return rows(sql, from, to).stream()
                .map(r -> new CountRow((String) r[0], (String) r[1], ((Number) r[2]).longValue())).toList();
    }
}
