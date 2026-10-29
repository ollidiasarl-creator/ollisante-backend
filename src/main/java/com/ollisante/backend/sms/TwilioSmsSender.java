package com.ollisante.backend.sms;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * SMS libres (rappels de rendez-vous) par Twilio Messaging. Actif si TWILIO_SMS_FROM est renseigné : soit un
 * service de messagerie Twilio (MG…, recommandé : Twilio choisit l'expéditeur adapté à chaque pays), soit un
 * numéro ou un nom d'expéditeur (« Ollisante »). Sans lui, les rappels partent seulement par e-mail.
 */
@Component
@Primary
@ConditionalOnExpression("!'${ollisante.twilio.account-sid:}'.isBlank() && !'${ollisante.twilio.auth-token:}'.isBlank()"
        + " && !'${ollisante.twilio.sms-from:}'.isBlank()")
@Slf4j
public class TwilioSmsSender implements SmsSender {

    private final RestClient http;
    private final String from;

    public TwilioSmsSender(@Value("${ollisante.twilio.account-sid}") String accountSid,
                           @Value("${ollisante.twilio.auth-token}") String authToken,
                           @Value("${ollisante.twilio.sms-from}") String from) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(10_000);
        timeouts.setReadTimeout(20_000);
        String basic = Base64.getEncoder()
                .encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));
        this.http = RestClient.builder()
                .baseUrl("https://api.twilio.com/2010-04-01/Accounts/" + accountSid)
                .requestFactory(timeouts)
                .defaultHeader("Authorization", "Basic " + basic)
                .build();
        this.from = from.trim();
    }

    @Override
    public void send(String toE164, String message) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", toE164);
        form.add(from.startsWith("MG") ? "MessagingServiceSid" : "From", from);
        form.add("Body", message);
        http.post()
                .uri("/Messages.json")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity();
    }
}
