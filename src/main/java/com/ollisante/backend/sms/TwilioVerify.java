package com.ollisante.backend.sms;

import com.fasterxml.jackson.databind.JsonNode;
import com.ollisante.backend.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Twilio Verify : Twilio génère, envoie et vérifie le code SMS (pas besoin de numéro expéditeur par pays).
 * Actif seulement si les trois identifiants sont configurés.
 */
@Component
@ConditionalOnExpression("!'${ollisante.twilio.account-sid:}'.isBlank() && !'${ollisante.twilio.auth-token:}'.isBlank()"
        + " && !'${ollisante.twilio.verify-service-sid:}'.isBlank()")
@Slf4j
public class TwilioVerify {

    private final RestClient http;

    public TwilioVerify(@Value("${ollisante.twilio.account-sid}") String accountSid,
                        @Value("${ollisante.twilio.auth-token}") String authToken,
                        @Value("${ollisante.twilio.verify-service-sid}") String serviceSid) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(10_000);
        timeouts.setReadTimeout(20_000);
        String basic = Base64.getEncoder()
                .encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));
        this.http = RestClient.builder()
                .baseUrl("https://verify.twilio.com/v2/Services/" + serviceSid)
                .requestFactory(timeouts)
                .defaultHeader("Authorization", "Basic " + basic)
                .build();
    }

    /** Envoie un code par SMS au numéro (format international). */
    public void start(String phone, String locale) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", phone);
        form.add("Channel", "sms");
        form.add("Locale", "en".equals(locale) ? "en" : "fr");
        try {
            post("/Verifications", form);
        } catch (RestClientResponseException e) {
            log.warn("Twilio Verify : envoi refusé ({}) : {}", e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 400) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "NUMERO_NON_JOIGNABLE",
                        "Impossible d'envoyer un SMS à ce numéro. Vérifiez-le ou connectez-vous avec votre e-mail.");
            }
            throw unavailable();
        } catch (RestClientException e) {
            log.warn("Twilio Verify injoignable : {}", e.getMessage());
            throw unavailable();
        }
    }

    /** true si le code saisi est le bon ; false s'il est faux, expiré ou déjà utilisé. */
    public boolean check(String phone, String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", phone);
        form.add("Code", code);
        try {
            JsonNode body = post("/VerificationCheck", form);
            return body != null && "approved".equals(body.path("status").asText());
        } catch (RestClientResponseException e) {
            // 404 : aucune vérification en cours (code expiré, déjà validé ou trop d'essais).
            if (e.getStatusCode().value() == 404 || e.getStatusCode().value() == 400) return false;
            log.warn("Twilio Verify : vérification refusée ({}) : {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw unavailable();
        } catch (RestClientException e) {
            log.warn("Twilio Verify injoignable : {}", e.getMessage());
            throw unavailable();
        }
    }

    private JsonNode post(String path, MultiValueMap<String, String> form) {
        return http.post().uri(path).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                .retrieve().body(JsonNode.class);
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SMS_INDISPONIBLE",
                "L'envoi de SMS est momentanément indisponible. Réessayez ou connectez-vous avec votre e-mail.");
    }
}
