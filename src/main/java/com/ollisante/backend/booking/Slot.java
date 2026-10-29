package com.ollisante.backend.booking;

import com.ollisante.backend.domain.AppointmentMode;
import java.time.Instant;
import java.util.UUID;

public record Slot(Instant startsAt, Instant endsAt, AppointmentMode mode, UUID facilityId) {
}
