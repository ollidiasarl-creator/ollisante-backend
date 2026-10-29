package com.ollisante.backend.dto;

import com.ollisante.backend.domain.AppointmentMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Heures en heure locale du pays du praticien, au format "HH:mm". weekday : 1 = lundi … 7 = dimanche. */
public record AvailabilityRuleRequest(
        @NotNull @Min(1) @Max(7) Integer weekday,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @Min(5) @Max(240) Integer slotMinutes,
        @NotNull AppointmentMode mode,
        UUID facilityId,
        LocalDate validFrom,
        LocalDate validUntil) {
}
