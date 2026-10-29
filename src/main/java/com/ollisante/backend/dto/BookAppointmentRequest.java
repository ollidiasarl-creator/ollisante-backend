package com.ollisante.backend.dto;

import com.ollisante.backend.domain.AppointmentMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * startsAt : instant UTC exact d'un créneau renvoyé par /practitioners/{id}/slots, ex. 2026-10-12T09:00:00Z.
 * paymentMethod : moyen de paiement choisi par le patient parmi ceux activés pour le pays (null : anciennes
 * versions de l'application, Mobile Money si proposé, sinon sur place).
 * relativeId : rendez-vous pris pour l'un de ses proches (null : pour soi).
 */
public record BookAppointmentRequest(
        @NotNull UUID practitionerId,
        @NotNull Instant startsAt,
        @NotNull AppointmentMode mode,
        @Size(max = 1000) String reason,
        PaymentMethod paymentMethod,
        UUID relativeId) {

    public enum PaymentMethod { MOBILE_MONEY, ON_SITE }
}
