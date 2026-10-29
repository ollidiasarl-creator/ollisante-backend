package com.ollisante.backend.service;

import com.ollisante.backend.common.ApiException;
import com.ollisante.backend.config.JwtProperties;
import com.ollisante.backend.config.OtpProperties;
import com.ollisante.backend.domain.EmailSignup;
import com.ollisante.backend.dto.AuthResponse;
import com.ollisante.backend.dto.EmailSignupVerify;
import com.ollisante.backend.dto.RegisterPatientRequest;
import com.ollisante.backend.mail.EmailSender;
import com.ollisante.backend.repository.EmailSignupRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inscription patient par e-mail en deux temps : un code est envoyé à l'adresse, le compte n'est créé
 * qu'une fois ce code saisi. Évite les comptes ouverts avec l'adresse de quelqu'un d'autre.
 */
@Service
@RequiredArgsConstructor
public class EmailSignupService {

    private static final SecureRandom RANDOM = new SecureRandom();
    /** Un e-mail peut mettre quelques minutes à arriver : le code reste valable plus longtemps qu'un SMS. */
    private static final int TTL_MINUTES = 15;

    private final EmailSignupRepository signups;
    private final AuthService authService;
    private final EmailSender emailSender;
    private final OtpProperties properties;
    private final JwtProperties jwtProperties;

    /** Si l'envoi de l'e-mail échoue, la transaction est annulée : rien n'est enregistré. */
    @Transactional
    public long start(RegisterPatientRequest r) {
        authService.checkPatientSignup(r);
        String email = normalize(r.email());
        Instant now = Instant.now();
        signups.findFirstByEmailOrderByCreatedAtDesc(email).ifPresent(last -> {
            if (last.getCreatedAt().isAfter(now.minusSeconds(properties.resendSeconds()))) throw tooManyRequests();
        });
        if (signups.countByEmailAndCreatedAtAfter(email, now.minus(24, ChronoUnit.HOURS)) >= properties.dailyLimit()) {
            throw tooManyRequests();
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        EmailSignup s = new EmailSignup();
        s.setEmail(email);
        s.setPasswordHash(authService.encodePassword(r.password()));
        s.setFirstName(r.firstName().trim());
        s.setLastName(r.lastName().trim());
        s.setCountryCode(r.countryCode() == null ? null : r.countryCode().toUpperCase(Locale.ROOT));
        s.setBirthDate(r.birthDate());
        s.setLocale(r.locale());
        s.setCodeHash(OtpService.hash(jwtProperties.secret(), "signup:" + email, code));
        s.setExpiresAt(now.plus(TTL_MINUTES, ChronoUnit.MINUTES));
        signups.save(s);

        boolean english = "en".equals(r.locale());
        emailSender.send(email, subject(english), body(s.getFirstName(), code, english));
        return TTL_MINUTES * 60L;
    }

    /** noRollbackFor : le compteur d'essais est conservé même quand le code est faux. */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse verify(EmailSignupVerify r) {
        String email = normalize(r.email());
        Instant now = Instant.now();
        EmailSignup s = signups.findFirstByEmailAndConsumedFalseOrderByCreatedAtDesc(email)
                .filter(x -> x.getExpiresAt().isAfter(now) && x.getAttempts() < properties.maxAttempts())
                .orElseThrow(EmailSignupService::invalidCode);
        String expected = OtpService.hash(jwtProperties.secret(), "signup:" + email, r.code());
        if (!MessageDigest.isEqual(s.getCodeHash().getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8))) {
            s.setAttempts(s.getAttempts() + 1);
            throw invalidCode();
        }
        s.setConsumed(true);
        signups.saveAndFlush(s);
        return authService.completeEmailSignup(s);
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String subject(boolean english) {
        return english ? "Ollisanté: confirm your e-mail address" : "Ollisanté : confirmez votre adresse e-mail";
    }

    private static String body(String firstName, String code, boolean english) {
        if (english) {
            return "Hello " + firstName + ",\n\nYour code to confirm your Ollisanté account is: " + code
                    + "\n\nIt expires in " + TTL_MINUTES + " minutes. If you did not create an account, ignore this"
                    + " e-mail.\n\nThe Ollisanté team";
        }
        return "Bonjour " + firstName + ",\n\nVotre code pour confirmer votre compte Ollisanté est : " + code
                + "\n\nIl expire dans " + TTL_MINUTES + " minutes. Si vous n'avez pas créé de compte, ignorez cet"
                + " e-mail.\n\nL'équipe Ollisanté";
    }

    private static ApiException invalidCode() {
        return new ApiException(HttpStatus.BAD_REQUEST, "CODE_INVALIDE", "Code incorrect ou expiré.");
    }

    private static ApiException tooManyRequests() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TROP_DE_DEMANDES",
                "Trop de demandes de code. Veuillez réessayer plus tard.");
    }
}
