package com.ollisante.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordForgotRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @Pattern(regexp = "fr|en", message = "Langue acceptée : fr ou en") String locale) {
}
