package com.ollisante.backend.reminder;

import com.ollisante.backend.domain.Appointment;
import com.ollisante.backend.domain.AppointmentStatus;
import com.ollisante.backend.domain.Country;
import com.ollisante.backend.domain.Organization;
import com.ollisante.backend.domain.User;
import com.ollisante.backend.mail.EmailSender;
import com.ollisante.backend.repository.AppointmentRepository;
import com.ollisante.backend.repository.CountryRepository;
import com.ollisante.backend.repository.OrganizationRepository;
import com.ollisante.backend.sms.SmsSender;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rappels des rendez-vous confirmés, dans tous les pays (réservations faites dans l'application ou sur le site) :
 * <ul>
 *   <li>patient : la veille (24 h avant) par e-mail et SMS, puis 2 h avant par SMS ;</li>
 *   <li>médecin : chaque soir à partir de 18 h (heure du pays), la liste de ses rendez-vous du lendemain ;</li>
 *   <li>structure : chaque soir, la liste des rendez-vous du lendemain dans ses lieux de consultation.</li>
 * </ul>
 * Chaque message n'est envoyé qu'une fois (table reminder_log). Un rendez-vous pris moins de 30 minutes avant
 * l'heure du rappel ne déclenche pas de rappel (la confirmation de réservation vient d'être affichée).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AppointmentReminderJob {

    private static final Duration DAY_BEFORE = Duration.ofHours(24);
    private static final Duration SOON = Duration.ofHours(2);
    /** En dessous, le rappel de la veille est inutile : celui de 2 h avant suffit. */
    private static final Duration DAY_BEFORE_MIN = Duration.ofHours(3);
    private static final Duration JUST_BOOKED = Duration.ofMinutes(30);

    private final AppointmentRepository appointments;
    private final CountryRepository countries;
    private final OrganizationRepository organizations;
    private final EmailSender email;
    private final SmsSender sms;
    private final JdbcTemplate jdbc;

    @Value("${ollisante.reminders.enabled:true}")
    private boolean enabled;

    @Value("${ollisante.reminders.digest-hour:18}")
    private int digestHour;

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT2M")
    @Transactional
    public void run() {
        if (!enabled) return;
        Instant now = Instant.now();
        List<Appointment> upcoming = appointments.findForReminders(AppointmentStatus.CONFIRMED, now,
                now.plus(Duration.ofHours(48)));
        Map<String, ZoneId> zones = new HashMap<>();
        for (Appointment a : upcoming) {
            remindPatient(a, now, zone(a, zones));
        }
        practitionerDigests(upcoming, now, zones);
        structureDigests(upcoming, now, zones);
    }

    // ---------- patient ----------

    private void remindPatient(Appointment a, Instant now, ZoneId zone) {
        if (a.getCreatedAt() != null && a.getCreatedAt().isAfter(now.minus(JUST_BOOKED))) return;
        Duration left = Duration.between(now, a.getStartsAt());
        User user = a.getPatient().getUser();
        boolean en = "en".equals(user.getLocale());
        String key = "A:" + a.getId();
        if (left.compareTo(DAY_BEFORE) <= 0 && left.compareTo(DAY_BEFORE_MIN) > 0) {
            if (user.getEmail() != null && user.isNotifyEmail() && claim(key + ":J1:MAIL")) {
                ReminderMessages.Mail m = ReminderMessages.patientEmail(a, zone, en);
                sendEmail(user.getEmail(), m);
            }
            if (user.getPhone() != null && user.isNotifySms() && claim(key + ":J1:SMS")) {
                sendSms(user.getPhone(), ReminderMessages.patientSmsDayBefore(a, zone, en));
            }
        }
        if (left.compareTo(SOON) <= 0 && user.getPhone() != null && user.isNotifySms() && claim(key + ":H2:SMS")) {
            sendSms(user.getPhone(), ReminderMessages.patientSmsSoon(a, zone, en));
        }
    }

    // ---------- médecin : liste du lendemain ----------

    private void practitionerDigests(List<Appointment> upcoming, Instant now, Map<String, ZoneId> zones) {
        Map<UUID, List<Appointment>> byPractitioner = new LinkedHashMap<>();
        for (Appointment a : upcoming) {
            byPractitioner.computeIfAbsent(a.getPractitioner().getId(), k -> new ArrayList<>()).add(a);
        }
        byPractitioner.forEach((id, list) -> {
            ZoneId zone = zone(list.get(0), zones);
            LocalDate tomorrow = tomorrowIfEvening(now, zone);
            if (tomorrow == null) return;
            List<Appointment> day = onDate(list, tomorrow, zone);
            if (day.isEmpty()) return;
            User user = day.get(0).getPractitioner().getUser();
            boolean en = "en".equals(user.getLocale());
            String key = "P:" + id + ":" + tomorrow;
            if (user.getEmail() != null && user.isNotifyEmail() && claim(key + ":MAIL")) {
                sendEmail(user.getEmail(), ReminderMessages.practitionerDigest(day.get(0).getPractitioner(), day,
                        tomorrow, zone, en));
            }
            if (user.getPhone() != null && user.isNotifySms() && claim(key + ":SMS")) {
                sendSms(user.getPhone(), ReminderMessages.digestSms(day, zone, en));
            }
        });
    }

    // ---------- structure : liste du lendemain ----------

    private void structureDigests(List<Appointment> upcoming, Instant now, Map<String, ZoneId> zones) {
        Map<UUID, List<Appointment>> byStructure = new LinkedHashMap<>();
        for (Appointment a : upcoming) {
            if (a.getOrganizationId() != null) {
                byStructure.computeIfAbsent(a.getOrganizationId(), k -> new ArrayList<>()).add(a);
            }
        }
        byStructure.forEach((id, list) -> {
            Organization org = organizations.findById(id).orElse(null);
            if (org == null) return;
            ZoneId zone = zone(org.getCountryCode(), zones);
            LocalDate tomorrow = tomorrowIfEvening(now, zone);
            if (tomorrow == null) return;
            List<Appointment> day = onDate(list, tomorrow, zone);
            if (day.isEmpty()) return;
            User account = org.getUser();
            boolean en = account != null && "en".equals(account.getLocale());
            String to = firstNonBlank(org.getEmail(), account == null ? null : account.getEmail());
            String phone = firstNonBlank(org.getPhone(), account == null ? null : account.getPhone());
            String key = "O:" + id + ":" + tomorrow;
            if (to != null && claim(key + ":MAIL")) {
                sendEmail(to, ReminderMessages.structureDigest(org.getName(), day, tomorrow, zone, en));
            }
            if (phone != null && claim(key + ":SMS")) {
                sendSms(phone, ReminderMessages.digestSms(day, zone, en));
            }
        });
    }

    // ---------- outils ----------

    /** Le lendemain (heure du pays) une fois l'heure du récapitulatif passée, sinon null. */
    private LocalDate tomorrowIfEvening(Instant now, ZoneId zone) {
        ZonedDateTime local = now.atZone(zone);
        return local.getHour() >= digestHour ? local.toLocalDate().plusDays(1) : null;
    }

    private static List<Appointment> onDate(List<Appointment> list, LocalDate date, ZoneId zone) {
        return list.stream().filter(a -> a.getStartsAt().atZone(zone).toLocalDate().equals(date)).toList();
    }

    /** Fuseau du lieu du rendez-vous (sinon du pays du médecin) ; UTC si le pays n'en a pas. */
    private ZoneId zone(Appointment a, Map<String, ZoneId> cache) {
        String code = a.getFacility() != null && a.getFacility().getCountryCode() != null
                ? a.getFacility().getCountryCode() : a.getPractitioner().getCountryCode();
        return zone(code, cache);
    }

    private ZoneId zone(String countryCode, Map<String, ZoneId> cache) {
        if (countryCode == null) return ZoneOffset.UTC;
        return cache.computeIfAbsent(countryCode, code -> countries.findById(code)
                .map(Country::getTimezone)
                .filter(tz -> tz != null && !tz.isBlank())
                .map(tz -> {
                    try {
                        return ZoneId.of(tz);
                    } catch (RuntimeException e) {
                        return (ZoneId) ZoneOffset.UTC;
                    }
                })
                .orElse(ZoneOffset.UTC));
    }

    /** Réserve l'envoi d'un rappel : false s'il a déjà été envoyé. */
    private boolean claim(String key) {
        return jdbc.update("INSERT INTO reminder_log (reminder_key) VALUES (?) ON CONFLICT DO NOTHING", key) == 1;
    }

    private void sendEmail(String to, ReminderMessages.Mail m) {
        try {
            email.send(to, m.subject(), m.body());
        } catch (RuntimeException e) {
            log.warn("Rappel par e-mail non envoyé : {}", e.getMessage());
        }
    }

    private void sendSms(String to, String text) {
        try {
            sms.send(to, text);
        } catch (RuntimeException e) {
            log.warn("Rappel par SMS non envoyé : {}", e.getMessage());
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a.trim();
        return b != null && !b.isBlank() ? b.trim() : null;
    }
}
