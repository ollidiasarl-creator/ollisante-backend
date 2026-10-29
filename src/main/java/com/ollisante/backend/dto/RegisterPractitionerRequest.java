package com.ollisante.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RegisterPractitionerRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Numéro au format international, ex. +22370000000")
        String phone,
        @NotBlank @Size(min = 8, max = 72, message = "Le mot de passe doit contenir de 8 à 72 caractères")
        String password,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Size(min = 2, max = 2) String countryCode,
        @NotBlank @Size(max = 100) String licenseNumber,
        @NotEmpty List<@NotBlank String> specialtyCodes,
        @Pattern(regexp = "fr|en", message = "Langue acceptée : fr ou en") String locale) {
}
