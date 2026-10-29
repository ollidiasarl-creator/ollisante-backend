package com.ollisante.backend.mail;

/** Envoi d'e-mails transactionnels (codes de réinitialisation…). */
public interface EmailSender {

    void send(String to, String subject, String text);
}
