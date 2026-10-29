package com.ollisante.backend.reminder;

import com.ollisante.backend.domain.Appointment;
import com.ollisante.backend.domain.AppointmentMode;
import com.ollisante.backend.domain.Facility;
import com.ollisante.backend.domain.PaymentMode;
import com.ollisante.backend.domain.Practitioner;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/** Textes des rappels (français ou anglais selon le compte). Les SMS sont sans accents pour tenir en un seul SMS. */
final class ReminderMessages {

    static final String SITE = "https://ollisante.com";
    private static final Set<String> ZERO_DECIMAL = Set.of("XOF", "XAF", "GNF", "CDF", "RWF");
    private static final String FOOTER_FR = "\n\nOllisanté n'est pas un service d'urgence : en cas d'urgence, appelez le "
            + "numéro d'urgence de votre pays.\n\nL'équipe Ollisanté";
    private static final String FOOTER_EN = "\n\nOllisanté is not an emergency service: in an emergency, call your "
            + "country's emergency number.\n\nThe Ollisanté team";

    private ReminderMessages() {
    }

    record Mail(String subject, String body) {
    }

    // ---------- patient ----------

    static Mail patientEmail(Appointment a, ZoneId zone, boolean en) {
        String when = longDate(a, zone, en) + (en ? " at " : " à ") + time(a, zone);
        String subject = en ? "Reminder: your appointment on " + when : "Rappel : votre rendez-vous du " + when;
        String body = en
                ? "Hello " + a.getPatient().getFirstName() + ",\n\nThis is a reminder of your appointment:\n\n"
                        + (a.getRelativeName() != null ? "• For: " + a.getRelativeName() + "\n" : "")
                        + "• " + capitalize(when) + " (local time)\n• With " + doctor(a.getPractitioner()) + "\n• "
                        + place(a, true) + "\n• " + payment(a, true)
                        + "\n\nCan't make it? Cancel it in the app or on " + SITE + " (My appointments) so the slot "
                        + "can go to another patient." + FOOTER_EN
                : "Bonjour " + a.getPatient().getFirstName() + ",\n\nNous vous rappelons votre rendez-vous :\n\n"
                        + (a.getRelativeName() != null ? "• Pour : " + a.getRelativeName() + "\n" : "")
                        + "• " + capitalize(when) + " (heure locale)\n• Avec " + doctor(a.getPractitioner()) + "\n• "
                        + place(a, false) + "\n• " + payment(a, false)
                        + "\n\nUn empêchement ? Annulez dans l'application ou sur " + SITE + " (Mes rendez-vous) "
                        + "pour libérer le créneau." + FOOTER_FR;
        return new Mail(subject, body);
    }

    static String patientSmsDayBefore(Appointment a, ZoneId zone, boolean en) {
        String text = en
                ? "Ollisante: reminder of your appointment on " + shortDate(a, zone) + " at " + time(a, zone)
                        + " with " + doctor(a.getPractitioner()) + shortPlace(a, true) + ". Manage: ollisante.com"
                : "Ollisante : rappel de votre RDV le " + shortDate(a, zone) + " a " + time(a, zone) + " avec "
                        + doctor(a.getPractitioner()) + shortPlace(a, false) + ". Gerer : ollisante.com";
        return plain(text);
    }

    static String patientSmsSoon(Appointment a, ZoneId zone, boolean en) {
        String text = en
                ? "Ollisante: your appointment with " + doctor(a.getPractitioner()) + " is today at "
                        + time(a, zone) + shortPlace(a, true) + ". See you soon!"
                : "Ollisante : votre RDV avec " + doctor(a.getPractitioner()) + " est aujourd'hui a "
                        + time(a, zone) + shortPlace(a, false) + ". A tout a l'heure !";
        return plain(text);
    }

    // ---------- médecin et structure : récapitulatif du lendemain ----------

    static Mail practitionerDigest(Practitioner p, List<Appointment> day, LocalDate date, ZoneId zone, boolean en) {
        StringBuilder lines = new StringBuilder();
        for (Appointment a : day) {
            lines.append("• ").append(time(a, zone)).append(" — ").append(patientName(a))
                    .append(" — ").append(place(a, en));
            if (a.getReason() != null && !a.getReason().isBlank()) {
                lines.append(en ? " — Reason: " : " — Motif : ").append(a.getReason().trim());
            }
            lines.append('\n');
        }
        String dateText = date(date, en);
        String subject = en ? "Your appointments for tomorrow (" + dateText + ")"
                : "Vos rendez-vous de demain (" + dateText + ")";
        String body = en
                ? "Hello " + doctor(p) + ",\n\nYou have " + count(day.size(), true) + " tomorrow, " + dateText
                        + " (local time):\n\n" + lines + "\nDetails and changes in the Ollisanté app.\n\nThe Ollisanté team"
                : "Bonjour " + doctor(p) + ",\n\nVous avez " + count(day.size(), false) + " demain, " + dateText
                        + " (heure locale) :\n\n" + lines + "\nDétails et modifications dans l'application Ollisanté."
                        + "\n\nL'équipe Ollisanté";
        return new Mail(subject, body);
    }

    static Mail structureDigest(String structureName, List<Appointment> day, LocalDate date, ZoneId zone, boolean en) {
        StringBuilder lines = new StringBuilder();
        for (Appointment a : day) {
            lines.append("• ").append(time(a, zone)).append(" — ").append(doctor(a.getPractitioner()))
                    .append(" — ").append(patientName(a)).append('\n');
        }
        String dateText = date(date, en);
        String subject = en ? "Tomorrow's appointments at " + structureName + " (" + dateText + ")"
                : "Rendez-vous de demain à " + structureName + " (" + dateText + ")";
        String body = en
                ? "Hello,\n\n" + structureName + " has " + count(day.size(), true) + " tomorrow, " + dateText
                        + " (local time):\n\n" + lines + "\nFull agenda in your Structure space.\n\nThe Ollisanté team"
                : "Bonjour,\n\n" + structureName + " a " + count(day.size(), false) + " demain, " + dateText
                        + " (heure locale) :\n\n" + lines + "\nAgenda complet dans votre Espace Structure."
                        + "\n\nL'équipe Ollisanté";
        return new Mail(subject, body);
    }

    static String digestSms(List<Appointment> day, ZoneId zone, boolean en) {
        Appointment first = day.get(0);
        String text = en
                ? "Ollisante: tomorrow " + shortDate(first, zone) + ", " + count(day.size(), true) + ". First at "
                        + time(first, zone) + ". Details in the app."
                : "Ollisante : demain " + shortDate(first, zone) + ", " + count(day.size(), false) + ". Premier a "
                        + time(first, zone) + ". Detail dans l'application.";
        return plain(text);
    }

    // ---------- morceaux communs ----------

    static String doctor(Practitioner p) {
        return ("Dr " + nullToEmpty(p.getFirstName()) + " " + nullToEmpty(p.getLastName())).trim();
    }

    private static String patientName(Appointment a) {
        if (a.getRelativeName() != null) return a.getRelativeName();
        return (nullToEmpty(a.getPatient().getFirstName()) + " " + nullToEmpty(a.getPatient().getLastName())).trim();
    }

    private static String place(Appointment a, boolean en) {
        if (a.getMode() == AppointmentMode.VIDEO) {
            return en ? "Video consultation: join from the Ollisanté app" : "Téléconsultation : à rejoindre depuis l'application Ollisanté";
        }
        Facility f = a.getFacility();
        if (f == null) return en ? "In person" : "En cabinet";
        String where = f.getName();
        String address = String.join(", ", Stream.of(f.getAddress(), f.getCity())
                .filter(s -> s != null && !s.isBlank()).toList());
        return (en ? "Place: " : "Lieu : ") + where + (address.isEmpty() ? "" : " (" + address + ")");
    }

    private static String shortPlace(Appointment a, boolean en) {
        if (a.getMode() == AppointmentMode.VIDEO) return en ? " (video)" : " (teleconsultation)";
        Facility f = a.getFacility();
        if (f == null || f.getName() == null) return "";
        String name = f.getName().replace("(démo)", "").trim();
        return " (" + (name.length() > 32 ? name.substring(0, 32).trim() : name) + ")";
    }

    private static String payment(Appointment a, boolean en) {
        if (a.getPaymentMode() == PaymentMode.ONLINE) return en ? "Already paid online" : "Déjà payé en ligne";
        if (a.getPaymentMode() == PaymentMode.ON_SITE && a.getPriceMinor() != null) {
            return (en ? "To pay on site: " : "À régler sur place : ") + money(a.getPriceMinor(), a.getPriceCurrency());
        }
        return en ? "No fee" : "Sans frais";
    }

    private static String money(long minor, String currency) {
        String cur = currency == null ? "" : currency;
        String amount = ZERO_DECIMAL.contains(cur)
                ? String.format(Locale.FRANCE, "%,d", minor)
                : String.format(Locale.FRANCE, "%,.2f", minor / 100.0);
        return (amount + " " + cur).replace(' ', ' ').replace(' ', ' ').trim();
    }

    private static String count(int n, boolean en) {
        if (en) return n + (n > 1 ? " appointments" : " appointment");
        return n + " rendez-vous";
    }

    private static String time(Appointment a, ZoneId zone) {
        return DateTimeFormatter.ofPattern("HH:mm").format(a.getStartsAt().atZone(zone));
    }

    private static String shortDate(Appointment a, ZoneId zone) {
        return DateTimeFormatter.ofPattern("dd/MM").format(a.getStartsAt().atZone(zone));
    }

    private static String longDate(Appointment a, ZoneId zone, boolean en) {
        return date(a.getStartsAt().atZone(zone).toLocalDate(), en);
    }

    private static String date(LocalDate d, boolean en) {
        return DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", en ? Locale.UK : Locale.FRANCE).format(d);
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** SMS sans accents : un SMS standard (160 caractères) au lieu de 70 avec accents. */
    private static String plain(String s) {
        String noAccents = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.length() > 160 ? noAccents.substring(0, 157) + "..." : noAccents;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
