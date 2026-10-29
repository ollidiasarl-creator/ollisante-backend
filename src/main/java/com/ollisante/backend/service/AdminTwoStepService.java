package com.ollisante.backend.service;

import com.ollisante.backend.common.ApiException;
import com.ollisante.backend.config.JwtProperties;
import com.ollisante.backend.domain.AdminLoginChallenge;
import com.ollisante.backend.domain.Role;
import com.ollisante.backend.domain.TrustedDevice;
import com.ollisante.backend.domain.User;
import com.ollisante.backend.dto.AdminLoginDtos.Challenge;
import com.ollisante.backend.dto.AdminLoginDtos.Verified;
import com.ollisante.backend.dto.AdminLoginDtos.Verify;
import com.ollisante.backend.mail.EmailSender;
import com.ollisante.backend.repository.AdminLoginChallengeRepository;
import com.ollisante.backend.repository.TrustedDeviceRepository;
import com.ollisante.backend.repository.UserRepository;
import com.ollisante.backend.security.TokenHasher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Double vérification des administrateurs : après le mot de passe, un code à 6 chiffres est envoyé par e-mail.
 * « Se souvenir de cet ordinateur » le dispense du code pendant 30 jours. Désactivable en urgence
 * (ADMIN_TWO_STEP_ENABLED=false) si l'envoi d'e-mails tombait en panne.
 */
@Service
@RequiredArgsConstructor
public class AdminTwoStepService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CODE_MINUTES = 10;
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_CODES_PER_HOUR = 6;
    private static final int TRUST_DAYS = 30;

    private final AdminLoginChallengeRepository challenges;
    private final TrustedDeviceRepository devices;
    private final UserRepository users;
    private final AuthService authService;
    private final EmailSender emailSender;
    private final JwtProperties jwtProperties;

    @Value("${ollisante.admin-two-step.enabled:true}")
    private boolean enabled;

    /** Faut-il un code pour cette connexion ? Non pour les autres rôles, ni pour un ordinateur de confiance. */
    @Transactional(readOnly = true)
    public boolean required(User user, String deviceToken) {
        boolean admin = enabled && user.getRole() == Role.ADMIN;
        boolean chosen = user.isTwoStepEnabled() && user.getEmail() != null;
        if (!admin && !chosen) return false;
        if (deviceToken == null || deviceToken.isBlank()) return true;
        return devices.findByTokenHash(TokenHasher.sha256Hex(deviceToken))
                .filter(d -> d.getUserId().equals(user.getId()) && d.getExpiresAt().isAfter(Instant.now()))
                .isEmpty();
    }

    /** Si l'e-mail ne part pas, la transaction est annulée et l'administrateur voit un message clair. */
    @Transactional
    public Challenge challenge(User user) {
        Instant now = Instant.now();
        if (challenges.countByUserIdAndCreatedAtAfter(user.getId(), now.minus(1, ChronoUnit.HOURS)) >= MAX_CODES_PER_HOUR) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TROP_DE_DEMANDES",
                    "Trop de codes demandés. Réessayez dans une heure.");
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        AdminLoginChallenge c = new AdminLoginChallenge();
        c.setUserId(user.getId());
        c.setExpiresAt(now.plus(CODE_MINUTES, ChronoUnit.MINUTES));
        c.setCodeHash("pending");
        challenges.saveAndFlush(c);
        c.setCodeHash(hash(c, code));
        try {
            boolean admin = user.getRole() == Role.ADMIN;
            emailSender.send(user.getEmail(), admin ? "Ollisanté : code de connexion administrateur" : "Ollisanté : votre code de connexion",
                    "Bonjour,\n\nVotre code de connexion " + (admin ? "au centre d'administration Ollisanté" : "à Ollisanté") + " est : " + code
                            + "\n\nIl expire dans " + CODE_MINUTES + " minutes. Si vous n'êtes pas à l'origine de cette"
                            + " connexion, changez votre mot de passe sans attendre : quelqu'un le connaît.\n\nOllisanté");
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_INDISPONIBLE",
                    "Le code de connexion n'a pas pu être envoyé par e-mail. Réessayez dans quelques minutes.");
        }
        return new Challenge(true, c.getId(), hint(user.getEmail()), CODE_MINUTES * 60L);
    }

    /** noRollbackFor : le compteur d'essais est conservé même quand le code est faux. */
    @Transactional(noRollbackFor = ApiException.class)
    public Verified verify(Verify r) {
        Instant now = Instant.now();
        AdminLoginChallenge c = challenges.findById(r.challengeId())
                .filter(x -> !x.isConsumed() && x.getExpiresAt().isAfter(now) && x.getAttempts() < MAX_ATTEMPTS)
                .orElseThrow(AdminTwoStepService::invalidCode);
        if (!MessageDigest.isEqual(c.getCodeHash().getBytes(StandardCharsets.UTF_8),
                hash(c, r.code()).getBytes(StandardCharsets.UTF_8))) {
            c.setAttempts(c.getAttempts() + 1);
            throw invalidCode();
        }
        c.setConsumed(true);
        User user = users.findById(c.getUserId()).orElseThrow(AdminTwoStepService::invalidCode);
        String deviceToken = null;
        if (r.rememberDevice()) {
            byte[] raw = new byte[32];
            RANDOM.nextBytes(raw);
            deviceToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
            TrustedDevice d = new TrustedDevice();
            d.setUserId(user.getId());
            d.setTokenHash(TokenHasher.sha256Hex(deviceToken));
            d.setExpiresAt(now.plus(TRUST_DAYS, ChronoUnit.DAYS));
            devices.save(d);
        }
        return new Verified(authService.tokensFor(user), deviceToken);
    }

    private String hash(AdminLoginChallenge c, String code) {
        return OtpService.hash(jwtProperties.secret(), "admin:" + c.getId(), code);
    }

    /** « ad•••@ollisante.com » : rappelle où chercher le code sans afficher l'adresse complète. */
    static String hint(String email) {
        int at = email.indexOf('@');
        if (at <= 0) return email;
        return email.substring(0, Math.min(2, at)) + "•••" + email.substring(at);
    }

    private static ApiException invalidCode() {
        return new ApiException(HttpStatus.BAD_REQUEST, "CODE_INVALIDE", "Code incorrect ou expiré.");
    }
}
