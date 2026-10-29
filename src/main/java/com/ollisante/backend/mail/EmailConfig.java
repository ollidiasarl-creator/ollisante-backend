package com.ollisante.backend.mail;

import com.ollisante.backend.common.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * SMTP si SPRING_MAIL_HOST est défini ; sinon, en développement local, l'e-mail s'affiche dans la console ;
 * sinon, refus propre tant que l'envoi n'est pas configuré.
 */
@Configuration
@Slf4j
public class EmailConfig {

    @Bean
    public EmailSender emailSender(ObjectProvider<JavaMailSender> javaMailSender, Environment environment,
                                   @Value("${ollisante.mail.from:}") String from) {
        JavaMailSender smtp = javaMailSender.getIfAvailable();
        if (smtp != null && !from.isBlank()) {
            return new SmtpEmailSender(smtp, from);
        }
        if (environment.acceptsProfiles(Profiles.of("local"))) {
            return (to, subject, text) -> log.info("[E-mail simulé] à {} : {}\n{}", to, subject, text);
        }
        return (to, subject, text) -> {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_NON_CONFIGURE",
                    "L'envoi d'e-mails n'est pas encore configuré.");
        };
    }
}
