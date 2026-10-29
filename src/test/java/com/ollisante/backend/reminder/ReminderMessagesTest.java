package com.ollisante.backend.reminder;

import static org.assertj.core.api.Assertions.assertThat;

import com.ollisante.backend.domain.Appointment;
import com.ollisante.backend.domain.AppointmentMode;
import com.ollisante.backend.domain.Facility;
import com.ollisante.backend.domain.Patient;
import com.ollisante.backend.domain.PaymentMode;
import com.ollisante.backend.domain.Practitioner;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReminderMessagesTest {

    private static final ZoneId DAKAR = ZoneId.of("Africa/Dakar");

    private static Appointment appointment() {
        Patient patient = new Patient();
        patient.setFirstName("Aïssatou");
        patient.setLastName("Ba");
        Practitioner doctor = new Practitioner();
        doctor.setFirstName("Moussa");
        doctor.setLastName("Ndiaye");
        Facility facility = new Facility();
        facility.setName("Polyclinique Kër Ollisanté (démo)");
        facility.setCity("Dakar");
        Appointment a = new Appointment();
        a.setPatient(patient);
        a.setPractitioner(doctor);
        a.setFacility(facility);
        a.setMode(AppointmentMode.IN_PERSON);
        a.setStartsAt(Instant.parse("2026-10-23T10:30:00Z"));
        a.setPaymentMode(PaymentMode.ON_SITE);
        a.setPriceMinor(10_000L);
        a.setPriceCurrency("XOF");
        return a;
    }

    @Test
    void patientEmailGivesTimePlaceAndPayment() {
        ReminderMessages.Mail mail = ReminderMessages.patientEmail(appointment(), DAKAR, false);
        assertThat(mail.subject()).isEqualTo("Rappel : votre rendez-vous du vendredi 23 octobre 2026 à 10:30");
        assertThat(mail.body()).contains("Bonjour Aïssatou", "Dr Moussa Ndiaye", "Polyclinique Kër Ollisanté",
                "À régler sur place : 10 000 XOF", "https://ollisante.com");
    }

    @Test
    void smsFitInOneMessageWithoutAccents() {
        Appointment a = appointment();
        for (String sms : List.of(ReminderMessages.patientSmsDayBefore(a, DAKAR, false),
                ReminderMessages.patientSmsSoon(a, DAKAR, false), ReminderMessages.digestSms(List.of(a), DAKAR, true))) {
            assertThat(sms.length()).isLessThanOrEqualTo(160);
            assertThat(sms).matches("\\p{ASCII}*");
        }
        assertThat(ReminderMessages.patientSmsDayBefore(a, DAKAR, false))
                .startsWith("Ollisante : rappel de votre RDV le 23/10 a 10:30 avec Dr Moussa Ndiaye");
    }

    @Test
    void practitionerDigestListsTheDay() {
        Appointment a = appointment();
        a.setReason("Fièvre");
        ReminderMessages.Mail mail = ReminderMessages.practitionerDigest(a.getPractitioner(), List.of(a),
                LocalDate.of(2026, 10, 23), DAKAR, false);
        assertThat(mail.subject()).isEqualTo("Vos rendez-vous de demain (vendredi 23 octobre 2026)");
        assertThat(mail.body()).contains("1 rendez-vous demain", "10:30 — Aïssatou Ba", "Motif : Fièvre");
    }
}
