package com.ollisante.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "Le code contient 6 chiffres") String code,
        @NotBlank @Size(min = 8, max = 72, message = "Le mot de passe doit contenir de 8 à 72 caractères")
        String newPassword) {
}
