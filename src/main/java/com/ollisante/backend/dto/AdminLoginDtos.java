package com.ollisante.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

/** Double vérification des administrateurs. */
public final class AdminLoginDtos {

    private AdminLoginDtos() {
    }

    /** Réponse 202 à /auth/login : mot de passe correct, code envoyé par e-mail. */
    public record Challenge(boolean verificationRequired, UUID challengeId, String emailHint, long expiresInSeconds) {
    }

    public record Verify(
            @NotNull UUID challengeId,
            @NotBlank @Pattern(regexp = "^\\d{6}$", message = "Le code contient 6 chiffres") String code,
            boolean rememberDevice) {
    }

    /** deviceToken : à renvoyer dans les prochaines connexions pour ne plus demander de code (30 jours). */
    public record Verified(AuthResponse auth, String deviceToken) {
    }
}
