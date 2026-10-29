package com.ollisante.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpRequest(
        @NotBlank @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Numéro au format international, ex. +22370000000")
        String phone,
        @Pattern(regexp = "fr|en", message = "Langue acceptée : fr ou en") String locale) {
}
