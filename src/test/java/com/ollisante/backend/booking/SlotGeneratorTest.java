package com.ollisante.backend.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ollisante.backend.domain.AppointmentMode;
import com.ollisante.backend.domain.AvailabilityRule;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class SlotGeneratorTest {

    private static final ZoneId BAMAKO = ZoneId.of("Africa/Bamako"); // UTC+0 toute l'année
    // 2026-10-05 est un lundi (weekday = 1)
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

    private AvailabilityRule rule(int weekday, String start, String end, int step) {
        AvailabilityRule r = new AvailabilityRule();
        r.setWeekday(weekday);
        r.setStartTime(LocalTime.parse(start));
        r.setEndTime(LocalTime.parse(end));
        r.setSlotMinutes(step);
        r.setMode(AppointmentMode.VIDEO);
        r.setValidFrom(LocalDate.of(2026, 1, 1));
        return r;
    }

    @Test
    void mondayRuleProducesFourHalfHourSlots() {
        List<Slot> slots = SlotGenerator.generate(List.of(rule(1, "09:00", "11:00", 30)), BAMAKO, MONDAY, MONDAY);

        assertEquals(4, slots.size());
        assertEquals(Instant.parse("2026-10-05T09:00:00Z"), slots.get(0).startsAt());
        assertEquals(Instant.parse("2026-10-05T09:30:00Z"), slots.get(0).endsAt());
        assertEquals(Instant.parse("2026-10-05T10:30:00Z"), slots.get(3).startsAt());
    }

    @Test
    void ruleDoesNotApplyOnOtherWeekdays() {
        List<Slot> slots = SlotGenerator.generate(
                List.of(rule(1, "09:00", "11:00", 30)), BAMAKO, MONDAY.plusDays(1), MONDAY.plusDays(6));

        assertTrue(slots.isEmpty());
    }

    @Test
    void lastSlotMustFitInsideTheRange() {
        List<Slot> slots = SlotGenerator.generate(List.of(rule(1, "09:00", "10:45", 30)), BAMAKO, MONDAY, MONDAY);

        assertEquals(3, slots.size()); // 09:00, 09:30, 10:00 — 10:30 dépasserait 10:45
    }

    @Test
    void validityWindowIsRespected() {
        AvailabilityRule r = rule(1, "09:00", "10:00", 30);
        r.setValidFrom(MONDAY.plusDays(7)); // commence le lundi suivant

        assertTrue(SlotGenerator.generate(List.of(r), BAMAKO, MONDAY, MONDAY).isEmpty());
        assertEquals(2, SlotGenerator.generate(List.of(r), BAMAKO, MONDAY, MONDAY.plusDays(7)).size());
    }

    @Test
    void localTimeIsConvertedUsingTheCountryZone() {
        // Lagos = UTC+1 : 09:00 locale = 08:00 UTC
        List<Slot> slots = SlotGenerator.generate(
                List.of(rule(1, "09:00", "09:30", 30)), ZoneId.of("Africa/Lagos"), MONDAY, MONDAY);

        assertEquals(Instant.parse("2026-10-05T08:00:00Z"), slots.get(0).startsAt());
    }

    @Test
    void duplicatedRulesDoNotDuplicateSlots() {
        List<Slot> slots = SlotGenerator.generate(
                List.of(rule(1, "09:00", "10:00", 30), rule(1, "09:00", "10:00", 30)), BAMAKO, MONDAY, MONDAY);

        assertEquals(2, slots.size());
    }
}
