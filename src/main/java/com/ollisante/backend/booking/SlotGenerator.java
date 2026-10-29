package com.ollisante.backend.booking;

import com.ollisante.backend.domain.AvailabilityRule;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Transforme des plages hebdomadaires en créneaux datés. Aucune dépendance à Spring ni à la base. */
public final class SlotGenerator {

    private SlotGenerator() {
    }

    public static List<Slot> generate(Collection<AvailabilityRule> rules, ZoneId zone, LocalDate from, LocalDate to) {
        // Le TreeSet dédoublonne deux plages qui produiraient le même créneau.
        Set<Slot> slots = new TreeSet<>(Comparator.comparing(Slot::startsAt).thenComparing(Slot::mode));

        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            int weekday = day.getDayOfWeek().getValue();
            for (AvailabilityRule rule : rules) {
                if (rule.getWeekday() != weekday) {
                    continue;
                }
                if (day.isBefore(rule.getValidFrom())) {
                    continue;
                }
                if (rule.getValidUntil() != null && day.isAfter(rule.getValidUntil())) {
                    continue;
                }
                int step = rule.getSlotMinutes();
                int startMinute = rule.getStartTime().toSecondOfDay() / 60;
                int endMinute = rule.getEndTime().toSecondOfDay() / 60;
                for (int minute = startMinute; minute + step <= endMinute; minute += step) {
                    ZonedDateTime start = ZonedDateTime.of(day, LocalTime.ofSecondOfDay(minute * 60L), zone);
                    ZonedDateTime end = start.plusMinutes(step);
                    slots.add(new Slot(start.toInstant(), end.toInstant(), rule.getMode(),
                            rule.getFacility() == null ? null : rule.getFacility().getId()));
                }
            }
        }
        return List.copyOf(slots);
    }
}
