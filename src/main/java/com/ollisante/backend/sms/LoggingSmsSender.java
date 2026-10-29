package com.ollisante.backend.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Développement local uniquement : affiche le SMS dans la console au lieu de l'envoyer. */
@Component
@Profile("local")
@Slf4j
public class LoggingSmsSender implements SmsSender {

    @Override
    public void send(String toE164, String message) {
        log.info("[SMS simulé] vers {} : {}", toE164, message);
    }
}
