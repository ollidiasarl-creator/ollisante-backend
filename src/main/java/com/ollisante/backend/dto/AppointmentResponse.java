package com.ollisante.backend.dto;

import com.ollisante.backend.domain.Appointment;
import com.ollisante.backend.domain.AppointmentMode;
import com.ollisante.backend.domain.AppointmentStatus;
import com.ollisante.backend.domain.PaymentMode;
import java.time.Instant;
import java.util.UUID;

public record AppointmentResponse(
        UUID id,
        UUID practitionerId,
        String practitionerName,
        String practitionerCountryCode,
        UUID patientId,
        String patientName,
        UUID facilityId,
        String facilityName,
        AppointmentMode mode,
        Instant startsAt,
        Instant endsAt,
        AppointmentStatus status,
        String reason,
        String videoRoom,
        Long priceMinor,
        String priceCurrency,
        PaymentMode paymentMode,
        /** Rendez-vous pris pour un proche : son nom (patientName le reprend), null sinon. */
        String relativeName) {

    /** À appeler dans une transaction (relations chargées à la demande). */
    public static AppointmentResponse from(Appointment a) {
        return new AppointmentResponse(
                a.getId(),
                a.getPractitioner().getId(),
                a.getPractitioner().getFirstName() + " " + a.getPractitioner().getLastName(),
                a.getPractitioner().getCountryCode(),
                a.getPatient().getId(),
                a.getRelativeName() != null ? a.getRelativeName()
                        : a.getPatient().getFirstName() + " " + a.getPatient().getLastName(),
                a.getFacility() == null ? null : a.getFacility().getId(),
                a.getFacility() == null ? null : a.getFacility().getName(),
                a.getMode(), a.getStartsAt(), a.getEndsAt(), a.getStatus(), a.getReason(), a.getVideoRoom(),
                a.getPriceMinor(), a.getPriceCurrency(), a.getPaymentMode(), a.getRelativeName());
    }
}
