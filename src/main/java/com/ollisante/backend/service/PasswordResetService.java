package com.ollisante.backend.service;

import com.ollisante.backend.common.ApiException;
import com.ollisante.backend.config.JwtProperties;
import com.ollisante.backend.config.OtpProperties;
import com.ollisante.backend.domain.PasswordResetCode;
import com.ollisante.backend.domain.User;
import com.ollisante.backend.dto.AuthResponse;
import com.ollisante.backend.dto.PasswordResetRequest;
import com.ollisante.backend.mail.EmailSender;
import com.ollisante.backend.repository.PasswordResetCodeRepository;
import com.ollisante.backend.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mot de passe oublié : code à 6 chiffres envoyé par e-mail, mêmes règles que le code SMS. */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final PasswordResetCodeRepository codes;
    private final EmailSender emailSender;
    private final AuthService authService;
    private final OtpProperties properties;
    private final JwtProperties jwtProperties;

    /**
     * Répond toujours de la même façon, que l'adresse ait un compte ou non, et même quand la limite d'envoi
     * est atteinte (une erreur révélerait qui est inscrit) : dans ce cas, simplement aucun e-mail ne part.
     * Si l'envoi échoue, la transaction est annulée : le code n'est pas enregistré.
     */
    @Transactional
    public long request(String rawEmail, String locale) {
        Optional<User> found = users.findByEmail(normalize(rawEmail));
        if (found.isEmpty()) return ttlSeconds();
        User user = found.get();

        Instant now = Instant.now();
        boolean tooSoon = codes.findFirstByUserIdOrderByCreatedAtDesc(user.getId())
                .filter(last -> last.getCreatedAt().isAfter(now.minusSeconds(properties.resendSeconds())))
                .isPresent();
        if (tooSoon || codes.countByUserIdAndCreatedAtAfter(user.getId(), now.minus(24, ChronoUnit.HOURS))
                >= properties.dailyLimit()) {
            return ttlSeconds();
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        PasswordResetCode reset = new PasswordResetCode();
        reset.setUserId(user.getId());
        reset.setCodeHash(OtpService.hash(jwtProperties.secret(), user.getId().toString(), code));
        reset.setExpiresAt(now.plus(properties.codeTtlMinutes(), ChronoUnit.MINUTES));
        codes.save(reset);

        boolean english = "en".equals(locale != null ? locale : user.getLocale());
        emailSender.send(user.getEmail(), subject(english), body(code, english));
        return ttlSeconds();
    }

    /** noRollbackFor : le compteur d'essais est conservé même quand le code est faux. */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse reset(PasswordResetRequest r) {
        User user = users.findByEmail(normalize(r.email())).orElseThrow(PasswordResetService::invalidCode);
        Instant now = Instant.now();
        PasswordResetCode reset = codes.findFirstByUserIdAndConsumedFalseOrderByCreatedAtDesc(user.getId())
                .filter(c -> c.getExpiresAt().isAfter(now) && c.getAttempts() < properties.maxAttempts())
                .orElseThrow(PasswordResetService::invalidCode);

        String expected = OtpService.hash(jwtProperties.secret(), user.getId().toString(), r.code());
        if (!MessageDigest.isEqual(reset.getCodeHash().getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8))) {
            reset.setAttempts(reset.getAttempts() + 1);
            throw invalidCode();
        }
        reset.setConsumed(true);
        codes.saveAndFlush(reset);
        return authService.completePasswordReset(user.getId(), r.newPassword());
    }

    private long ttlSeconds() {
        return properties.codeTtlMinutes() * 60L;
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String subject(boolean english) {
        return english ? "Ollisanté: reset your password" : "Ollisanté : réinitialisation de votre mot de passe";
    }

    private String body(String code, boolean english) {
        int minutes = properties.codeTtlMinutes();
        if (english) {
            return "Hello,\n\nYour code to choose a new Ollisanté password is: " + code + "\n\nIt expires in "
                    + minutes + " minutes. If you did not ask for it, ignore this e-mail: your password is unchanged."
                    + "\n\nThe Ollisanté team";
        }
        return "Bonjour,\n\nVotre code pour choisir un nouveau mot de passe Ollisanté est : " + code
                + "\n\nIl expire dans " + minutes + " minutes. Si vous n'êtes pas à l'origine de cette demande, "
                + "ignorez cet e-mail : votre mot de passe reste inchangé.\n\nL'équipe Ollisanté";
    }

    private static ApiException invalidCode() {
        return new ApiException(HttpStatus.BAD_REQUEST, "CODE_INVALIDE", "Code incorrect ou expiré.");
    }
}
