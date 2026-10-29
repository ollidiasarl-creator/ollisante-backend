package com.ollisante.backend.service;

import com.ollisante.backend.common.ApiException;
import com.ollisante.backend.config.JwtProperties;
import com.ollisante.backend.config.OtpProperties;
import com.ollisante.backend.domain.OtpCode;
import com.ollisante.backend.dto.AuthResponse;
import com.ollisante.backend.dto.OtpVerifyRequest;
import com.ollisante.backend.repository.OtpCodeRepository;
import com.ollisante.backend.sms.SmsSender;
import com.ollisante.backend.sms.TwilioVerify;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Connexion / inscription par téléphone : code à 6 chiffres envoyé par SMS. Avec Twilio Verify, Twilio génère
 * et vérifie le code ; les limites d'envoi et d'essais restent appliquées ici.
 */
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();
    /** Code géré par Twilio Verify : rien n'est stocké ici. */
    private static final String TWILIO_MARKER = "twilio-verify";

    private final OtpCodeRepository otps;
    private final SmsSender smsSender;
    private final AuthService authService;
    private final OtpProperties properties;
    private final JwtProperties jwtProperties;
    private final Optional<TwilioVerify> twilio;

    /** Répond toujours de la même façon, que le numéro ait déjà un compte ou non. */
    @Transactional
    public long request(String phone, String locale) {
        Instant now = Instant.now();
        otps.findFirstByPhoneOrderByCreatedAtDesc(phone).ifPresent(last -> {
            if (last.getCreatedAt().isAfter(now.minusSeconds(properties.resendSeconds()))) {
                throw tooManyRequests();
            }
        });
        if (otps.countByPhoneAndCreatedAtAfter(phone, now.minus(24, ChronoUnit.HOURS)) >= properties.dailyLimit()) {
            throw tooManyRequests();
        }

        OtpCode otp = new OtpCode();
        otp.setPhone(phone);
        otp.setExpiresAt(now.plus(properties.codeTtlMinutes(), ChronoUnit.MINUTES));
        if (twilio.isPresent()) {
            otp.setCodeHash(TWILIO_MARKER);
            otps.save(otp);
            twilio.get().start(phone, locale);
            return properties.codeTtlMinutes() * 60L;
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        otp.setCodeHash(hash(jwtProperties.secret(), phone, code));
        otps.save(otp);

        // Si l'envoi échoue (fournisseur absent ou en panne), la transaction est annulée : aucun code n'est consommé.
        smsSender.send(phone, message(code, locale));
        return properties.codeTtlMinutes() * 60L;
    }

    /**
     * noRollbackFor : le compteur d'essais doit être conservé même quand on répond par une erreur.
     * Le code n'est consommé qu'une fois la connexion réussie (NOM_REQUIS ne le consomme pas).
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse verify(OtpVerifyRequest r) {
        Instant now = Instant.now();
        OtpCode otp = otps.findFirstByPhoneAndConsumedFalseOrderByCreatedAtDesc(r.phone())
                .filter(o -> o.getExpiresAt().isAfter(now) && o.getAttempts() < properties.maxAttempts())
                .orElseThrow(OtpService::invalidCode);

        boolean matches;
        if (TWILIO_MARKER.equals(otp.getCodeHash())) {
            matches = twilio.map(t -> t.check(r.phone(), r.code())).orElse(false);
        } else {
            String expected = hash(jwtProperties.secret(), r.phone(), r.code());
            matches = MessageDigest.isEqual(
                    otp.getCodeHash().getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
        }
        if (!matches) {
            otp.setAttempts(otp.getAttempts() + 1);
            throw invalidCode();
        }

        AuthResponse response = authService.completePhoneLogin(r.phone(), r.firstName(), r.lastName(), r.locale());
        otp.setConsumed(true);
        return response;
    }

    /** HMAC-SHA256(secret, téléphone:code), en hexadécimal. Visible pour les tests. */
    static String hash(String secret, String phone, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((phone + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 indisponible", e);
        }
    }

    private String message(String code, String locale) {
        int minutes = properties.codeTtlMinutes();
        if ("en".equals(locale)) {
            return "Ollisanté: your sign-in code is " + code + ". It expires in " + minutes
                    + " minutes. Do not share it with anyone.";
        }
        return "Ollisanté : votre code de connexion est " + code + ". Il expire dans " + minutes
                + " minutes. Ne le communiquez à personne.";
    }

    private static ApiException invalidCode() {
        return new ApiException(HttpStatus.BAD_REQUEST, "CODE_INVALIDE", "Code incorrect ou expiré.");
    }

    private static ApiException tooManyRequests() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TROP_DE_DEMANDES",
                "Trop de demandes de code. Veuillez réessayer plus tard.");
    }
}
