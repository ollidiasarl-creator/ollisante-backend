package com.ollisante.backend.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** E-mail de bienvenue des comptes créés par l'équipe OLIDIA : la personne choisit elle-même son mot de passe. */
@Component
@RequiredArgsConstructor
@Slf4j
public class WelcomeMail {

    private final EmailSender emailSender;

    /**
     * @param greeting  « Dr Diop » ou « Khadija Ba »
     * @param profileFr profil à choisir dans l'application (« Médecin », « Patient »)
     * @return false si l'envoi a échoué (le compte reste créé)
     */
    public boolean send(String email, String greeting, String profileFr, String profileEn, String extraFr,
                        String extraEn, String locale) {
        boolean english = "en".equals(locale);
        String subject = english ? "Your Ollisanté account" : "Votre compte Ollisanté";
        String body = english
                ? "Hello " + greeting + ",\n\nThe OLIDIA team has created your Ollisanté account with this e-mail"
                        + " address." + extraEn + "\n\nTo choose your password: open the Ollisanté app, choose \""
                        + profileEn + "\", then \"With my e-mail address\", tap \"Forgot password?\" and enter this"
                        + " e-mail address. You will receive a code.\n\nThe Ollisanté team"
                : "Bonjour " + greeting + ",\n\nL'équipe OLIDIA a créé votre compte Ollisanté avec cette adresse"
                        + " e-mail." + extraFr + "\n\nPour choisir votre mot de passe : ouvrez l'application Ollisanté,"
                        + " choisissez « " + profileFr + " », puis « Avec mon adresse e-mail », touchez « Mot de passe"
                        + " oublié ? » et saisissez cette adresse. Vous recevrez un code.\n\nL'équipe Ollisanté";
        try {
            emailSender.send(email, subject, body);
            return true;
        } catch (RuntimeException e) {
            log.warn("E-mail de bienvenue non envoyé à {} : {}", email, e.getMessage());
            return false;
        }
    }
}
