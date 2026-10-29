package com.ollisante.backend.sms;

import com.ollisante.backend.common.ApiException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Valeur par défaut hors profil local : refuse proprement tant qu'aucun fournisseur SMS n'est branché. */
@Component
@Profile("!local")
public class UnconfiguredSmsSender implements SmsSender {

    @Override
    public void send(String toE164, String message) {
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SMS_NON_CONFIGURE",
                "L'envoi de SMS n'est pas encore configuré.");
    }
}
