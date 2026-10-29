package com.ollisante.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * identifier : adresse e-mail ou numéro de téléphone au format international.
 * deviceToken : facultatif, administrateurs uniquement — ordinateur « de confiance » dispensé du code e-mail.
 */
public record LoginRequest(@NotBlank String identifier, @NotBlank String password,
                           @Size(max = 200) String deviceToken) {
}
