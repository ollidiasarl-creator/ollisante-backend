package com.ollisante.backend.dto;

import com.ollisante.backend.domain.AppointmentMode;
import com.ollisante.backend.domain.AvailabilityRule;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AvailabilityRuleResponse(
        UUID id,
        int weekday,
        LocalTime startTime,
        LocalTime endTime,
        int slotMinutes,
        AppointmentMode mode,
        UUID facilityId,
        LocalDate validFrom,
        LocalDate validUntil) {

    public static AvailabilityRuleResponse from(AvailabilityRule r) {
        return new AvailabilityRuleResponse(r.getId(), r.getWeekday(), r.getStartTime(), r.getEndTime(),
                r.getSlotMinutes(), r.getMode(), r.getFacility() == null ? null : r.getFacility().getId(),
                r.getValidFrom(), r.getValidUntil());
    }
}
