package com.ollisante.backend.booking;

import com.ollisante.backend.config.BookingProperties;
import com.ollisante.backend.domain.AppointmentMode;
import com.ollisante.backend.domain.AppointmentStatus;
import com.ollisante.backend.domain.AvailabilityRule;
import com.ollisante.backend.domain.Practitioner;
import com.ollisante.backend.repository.AppointmentRepository;
import com.ollisante.backend.repository.AvailabilityRuleRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SlotService {

    public static final List<AppointmentStatus> ACTIVE_STATUSES =
            List.of(AppointmentStatus.PENDING_PAYMENT, AppointmentStatus.CONFIRMED);

    private final AvailabilityRuleRepository rules;
    private final AppointmentRepository appointments;
    private final TimeZones timeZones;
    private final BookingProperties booking;

    /** Créneaux encore libres : dans le futur (préavis respecté), pas trop lointains, et non déjà réservés. */
    @Transactional(readOnly = true)
    public List<Slot> freeSlots(Practitioner practitioner, LocalDate from, LocalDate to, AppointmentMode modeFilter) {
        ZoneId zone = timeZones.of(practitioner.getCountryCode());
        List<AvailabilityRule> practitionerRules = rules.findByPractitionerId(practitioner.getId());
        List<Slot> all = SlotGenerator.generate(practitionerRules, zone, from, to);

        Instant now = Instant.now();
        Instant earliest = now.plus(booking.minNoticeMinutes(), ChronoUnit.MINUTES);
        Instant latest = now.plus(booking.maxDaysAhead(), ChronoUnit.DAYS);

        Set<Instant> taken = new HashSet<>(appointments.findActiveStarts(
                practitioner.getId(),
                ACTIVE_STATUSES,
                from.atStartOfDay(zone).toInstant(),
                to.plusDays(1).atStartOfDay(zone).toInstant()));

        return all.stream()
                .filter(s -> modeFilter == null || s.mode() == modeFilter)
                .filter(s -> !s.startsAt().isBefore(earliest) && s.startsAt().isBefore(latest))
                .filter(s -> !taken.contains(s.startsAt()))
                .toList();
    }
}
