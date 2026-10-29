package com.ollisante.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Au moins un identifiant (email ou téléphone) est requis ; vérifié dans le service. */
public record RegisterPatientRequest(
        @Email @Size(max = 255) String email,
        @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Numéro au format international, ex. +22370000000")
        String phone,
        @NotBlank @Size(min = 8, max = 72, message = "Le mot de passe doit contenir de 8 à 72 caractères")
        String password,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Size(min = 2, max = 2) String countryCode,
        @Past LocalDate birthDate,
        @Pattern(regexp = "fr|en", message = "Langue acceptée : fr ou en") String locale) {
}
