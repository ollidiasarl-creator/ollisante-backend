package com.ollisante.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** firstName et lastName ne sont requis que pour un premier compte (sinon la réponse est NOM_REQUIS). */
public record OtpVerifyRequest(
        @NotBlank @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Numéro au format international, ex. +22370000000")
        String phone,
        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "Le code contient 6 chiffres") String code,
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @Pattern(regexp = "fr|en", message = "Langue acceptée : fr ou en") String locale) {
}
