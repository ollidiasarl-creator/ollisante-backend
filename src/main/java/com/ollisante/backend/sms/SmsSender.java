package com.ollisante.backend.sms;

/** Fournisseur d'envoi de SMS (à brancher : Twilio Verify, Infobip, Africa's Talking, Orange SMS API…). */
public interface SmsSender {

    void send(String toE164, String message);
}
