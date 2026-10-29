package com.ollisante.backend.dto;

import com.ollisante.backend.booking.Slot;
import com.ollisante.backend.domain.AppointmentMode;
import java.time.Instant;
import java.util.UUID;

public record SlotResponse(Instant startsAt, Instant endsAt, AppointmentMode mode, UUID facilityId) {

    public static SlotResponse from(Slot s) {
        return new SlotResponse(s.startsAt(), s.endsAt(), s.mode(), s.facilityId());
    }
}
