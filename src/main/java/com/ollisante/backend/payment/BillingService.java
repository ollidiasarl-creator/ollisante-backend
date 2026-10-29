package com.ollisante.backend.payment;

import com.ollisante.backend.common.ApiException;
import com.ollisante.backend.domain.Appointment;
import com.ollisante.backend.domain.AppointmentStatus;
import com.ollisante.backend.domain.Country;
import com.ollisante.backend.domain.Organization;
import com.ollisante.backend.domain.Patient;
import com.ollisante.backend.domain.Payment;
import com.ollisante.backend.domain.PaymentMode;
import com.ollisante.backend.domain.PaymentStatus;
import com.ollisante.backend.domain.Payout;
import com.ollisante.backend.domain.Practitioner;
import com.ollisante.backend.domain.User;
import com.ollisante.backend.mail.EmailSender;
import com.ollisante.backend.repository.AppointmentRepository;
import com.ollisante.backend.repository.CountryRepository;
import com.ollisante.backend.repository.OrganizationRepository;
import com.ollisante.backend.repository.PatientRepository;
import com.ollisante.backend.repository.PaymentRepository;
import com.ollisante.backend.repository.PayoutRepository;
import com.ollisante.backend.repository.PractitionerRepository;
import com.ollisante.backend.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rubrique « Paiements et reçus » du compte, pour la transparence de chacun :
 * <ul>
 *   <li>patient : moyens de paiement de son pays, historique (Mobile Money, sur place, remboursements) et reçus ;</li>
 *   <li>médecin : encaissements en ligne, commission OLIDIA, net à recevoir et reversements du vendredi ;</li>
 *   <li>structure : encaissements des rendez-vous pris dans ses lieux, commission et net.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class BillingService {

    private static final Set<PaymentStatus> COLLECTED =
            Set.of(PaymentStatus.SUCCEEDED, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED);

    private final UserRepository users;
    private final PatientRepository patients;
    private final PractitionerRepository practitioners;
    private final OrganizationRepository organizations;
    private final CountryRepository countries;
    private final AppointmentRepository appointments;
    private final PaymentRepository payments;
    private final PayoutRepository payouts;
    private final AccountingService accounting;
    private final PawaPay pawaPay;
    private final EmailSender email;

    // ---------- réponses ----------

    public record Billing(String role, PatientBilling patient, ProBilling pro) {
    }

    public record MobileMoney(String operator, String phone) {
    }

    /** methods : MOBILE_MONEY et/ou ON_SITE, tels que réglés pour le pays du patient. */
    public record PatientBilling(String countryCode, List<String> methods, MobileMoney lastMobileMoney,
                                 List<PatientItem> items) {
    }

    /**
     * status : PAID, PARTIALLY_REFUNDED, REFUNDED, PENDING (paiement en cours), ON_SITE (à régler ou réglé sur
     * place). Aucune commission ici : le patient ne voit que ce qu'il paie.
     */
    public record PatientItem(UUID appointmentId, UUID paymentId, Instant date, Instant appointmentAt,
                              String practitioner, String facility, long amountMinor, String currency, String method,
                              String status, long refundedMinor, String operator, String receiptNumber) {
    }

    public record Totals(String currency, int onlineCount, long grossMinor, long commissionMinor, long netMinor) {
    }

    public record ProPayment(UUID id, Instant date, Instant appointmentAt, String patient, String practitioner,
                             long amountMinor, long refundedMinor, long commissionMinor, long netMinor,
                             String currency, PaymentStatus status, boolean inPayout) {
    }

    public record ProPayout(UUID id, LocalDate periodEnd, String currency, long grossMinor, long commissionMinor,
                            long amountMinor, Payout.Status status, Instant paidAt, String reference) {
    }

    /** Statistiques du mois en cours et dernières opérations ; payouts vide pour une structure. */
    public record ProBilling(int commissionPercent, String month, List<Totals> monthTotals, int onSiteThisMonth,
                             List<ProPayout> payouts, List<ProPayment> payments) {
    }

    public record Receipt(String number, Instant issuedAt, String issuer, String payer, String practitioner,
                          String facility, Instant appointmentAt, long amountMinor, long refundedMinor,
                          String currency, String operator, String phone, PaymentStatus status) {
    }

    // ---------- lecture ----------

    @Transactional(readOnly = true)
    public Billing get(UUID userId) {
        User user = users.findById(userId).orElseThrow(BillingService::unauthenticated);
        return switch (user.getRole()) {
            case PATIENT -> new Billing("PATIENT", patient(user), null);
            case PRACTITIONER -> new Billing("PRACTITIONER", null, practitioner(userId));
            case STRUCTURE -> new Billing("STRUCTURE", null, structure(userId));
            default -> new Billing(user.getRole().name(), null, null);
        };
    }

    private PatientBilling patient(User user) {
        Patient patient = patients.findByUserId(user.getId()).orElse(null);
        String countryCode = patient == null ? null : patient.getCountryCode();
        List<String> methods = new ArrayList<>();
        Country country = countryCode == null ? null : countries.findById(countryCode).orElse(null);
        if (country != null) {
            if (country.isOnlinePayment() && pawaPay.enabled()) methods.add("MOBILE_MONEY");
            if (country.isOnSitePayment()) methods.add("ON_SITE");
        }
        List<PatientItem> items = new ArrayList<>();
        MobileMoney last = null;
        for (Payment p : payments.findForPayer(user.getId())) {
            Appointment a = p.getAppointment();
            if (last == null && p.getPhone() != null) last = new MobileMoney(p.getOperator(), mask(p.getPhone()));
            String status = switch (p.getStatus()) {
                case SUCCEEDED -> "PAID";
                case PARTIALLY_REFUNDED -> "PARTIALLY_REFUNDED";
                case REFUNDED -> "REFUNDED";
                default -> "PENDING";
            };
            items.add(new PatientItem(a.getId(), p.getId(), p.getCreatedAt(), a.getStartsAt(), doctor(a.getPractitioner()),
                    a.getFacility() == null ? null : a.getFacility().getName(), p.getAmountMinor(), p.getCurrency(),
                    "MOBILE_MONEY", status, p.getRefundedMinor(), p.getOperator(),
                    COLLECTED.contains(p.getStatus()) ? receiptNumber(p) : null));
        }
        if (patient != null) {
            for (Appointment a : appointments.findByPatientIdOrderByStartsAtDesc(patient.getId())) {
                // Paiements sur place : rendez-vous maintenus seulement (un rendez-vous annulé n'est pas dû).
                if (a.getPaymentMode() != PaymentMode.ON_SITE || a.getPriceMinor() == null) continue;
                if (a.getStatus() == AppointmentStatus.CANCELLED) continue;
                items.add(new PatientItem(a.getId(), null, a.getStartsAt(), a.getStartsAt(), doctor(a.getPractitioner()),
                        a.getFacility() == null ? null : a.getFacility().getName(), a.getPriceMinor(),
                        a.getPriceCurrency(), "ON_SITE", "ON_SITE", 0, null, null));
            }
        }
        items.sort(Comparator.comparing(PatientItem::date).reversed());
        return new PatientBilling(countryCode, methods, last, items);
    }

    private ProBilling practitioner(UUID userId) {
        Practitioner pr = practitioners.findByUserId(userId).orElseThrow(BillingService::unauthenticated);
        List<Payment> list = payments.findForPractitioner(pr.getId(), COLLECTED, PageRequest.of(0, 50));
        List<ProPayout> payoutRows = payouts.findByPractitionerIdOrderByPeriodEndDescCreatedAtDesc(pr.getId(),
                PageRequest.of(0, 20)).map(BillingService::payoutRow).getContent();
        long onSite = appointments.findByPractitionerIdOrderByStartsAtDesc(pr.getId()).stream()
                .filter(a -> a.getPaymentMode() == PaymentMode.ON_SITE && a.getStatus() != AppointmentStatus.CANCELLED)
                .filter(a -> thisMonth(a.getStartsAt())).count();
        return pro(list, payoutRows, (int) onSite, accounting.practitionerRate());
    }

    private ProBilling structure(UUID userId) {
        Organization org = organizations.findByUserId(userId).orElseThrow(BillingService::unauthenticated);
        List<Payment> list = payments.findForOrganization(org.getId(), COLLECTED, PageRequest.of(0, 50));
        return pro(list, List.of(), 0, accounting.structureRate(org));
    }

    private ProBilling pro(List<Payment> list, List<ProPayout> payoutRows, int onSite, int rate) {
        Map<String, long[]> totals = new LinkedHashMap<>();
        List<ProPayment> rows = new ArrayList<>();
        for (Payment p : list) {
            long net = p.getAmountMinor() - p.getRefundedMinor();
            long commission = Math.round(net * accounting.rateFor(p) / 100.0);
            Appointment a = p.getAppointment();
            rows.add(new ProPayment(p.getId(), p.getCreatedAt(), a.getStartsAt(),
                    (a.getPatient().getFirstName() + " " + a.getPatient().getLastName()).trim(),
                    doctor(a.getPractitioner()), p.getAmountMinor(), p.getRefundedMinor(), commission, net - commission,
                    p.getCurrency(), p.getStatus(), p.getPayoutId() != null));
            if (thisMonth(p.getCreatedAt())) {
                long[] t = totals.computeIfAbsent(p.getCurrency(), k -> new long[4]);
                t[0]++;
                t[1] += net;
                t[2] += commission;
                t[3] += net - commission;
            }
        }
        List<Totals> monthTotals = totals.entrySet().stream()
                .map(e -> new Totals(e.getKey(), (int) e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3]))
                .toList();
        String month = DateTimeFormatter.ofPattern("yyyy-MM").format(LocalDate.now(ZoneOffset.UTC));
        return new ProBilling(rate, month, monthTotals, onSite, payoutRows, rows);
    }

    // ---------- reçus ----------

    @Transactional(readOnly = true)
    public Receipt receipt(UUID userId, UUID paymentId) {
        Payment p = payments.findById(paymentId).orElseThrow(BillingService::notFound);
        Appointment a = p.getAppointment();
        boolean mine = userId.equals(p.getPayerUserId()) || userId.equals(a.getPatient().getUser().getId());
        if (!mine || !COLLECTED.contains(p.getStatus())) throw notFound();
        return new Receipt(receiptNumber(p), p.getUpdatedAt() != null ? p.getUpdatedAt() : p.getCreatedAt(), "OLIDIA SARL",
                (a.getPatient().getFirstName() + " " + a.getPatient().getLastName()).trim(), doctor(a.getPractitioner()),
                a.getFacility() == null ? null : a.getFacility().getName(), a.getStartsAt(), p.getAmountMinor(),
                p.getRefundedMinor(), p.getCurrency(), p.getOperator(), mask(p.getPhone()), p.getStatus());
    }

    /** Envoie le reçu à l'adresse e-mail du compte. */
    @Transactional(readOnly = true)
    public void emailReceipt(UUID userId, UUID paymentId) {
        User user = users.findById(userId).orElseThrow(BillingService::unauthenticated);
        if (user.getEmail() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAS_D_EMAIL",
                    "Aucune adresse e-mail n'est enregistrée sur ce compte.");
        }
        Receipt r = receipt(userId, paymentId);
        boolean en = "en".equals(user.getLocale());
        DateTimeFormatter day = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", en ? Locale.UK : Locale.FRANCE)
                .withZone(ZoneOffset.UTC);
        String amount = Money.format(r.amountMinor(), r.currency());
        String body = (en ? "Payment receipt " : "Reçu de paiement ") + r.number() + "\n\n"
                + (en ? "Issued by: " : "Émis par : ") + r.issuer() + " (Ollisanté)\n"
                + (en ? "Patient: " : "Patient : ") + r.payer() + "\n"
                + (en ? "Consultation: " : "Consultation : ") + r.practitioner()
                + (r.facility() == null ? "" : " — " + r.facility()) + "\n"
                + (en ? "Appointment: " : "Rendez-vous : ") + day.format(r.appointmentAt()) + " UTC\n"
                + (en ? "Amount paid: " : "Montant payé : ") + amount + "\n"
                + (en ? "Method: Mobile Money" : "Moyen de paiement : Mobile Money")
                + (r.operator() == null ? "" : " (" + r.operator() + (r.phone() == null ? "" : ", " + r.phone()) + ")") + "\n"
                + (r.refundedMinor() > 0 ? (en ? "Refunded: " : "Remboursé : ") + Money.format(r.refundedMinor(), r.currency()) + "\n" : "")
                + (en ? "Date: " : "Date : ") + day.format(r.issuedAt()) + " UTC\n\n"
                + (en ? "The Ollisanté team" : "L'équipe Ollisanté");
        email.send(user.getEmail(), (en ? "Your Ollisanté receipt " : "Votre reçu Ollisanté ") + r.number(), body);
    }

    // ---------- outils ----------

    /** Numéro de reçu lisible et stable : OLS-AAAAMMJJ-XXXXXX (début de l'identifiant du paiement). */
    static String receiptNumber(Payment p) {
        String day = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC).format(p.getCreatedAt());
        return "OLS-" + day + "-" + p.getId().toString().substring(0, 6).toUpperCase(Locale.ROOT);
    }

    /** 221771234567 → « •• •• •• 67 » : assez pour reconnaître son numéro, sans l'exposer en entier. */
    static String mask(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("\\D", "");
        return digits.length() < 4 ? null : "•• •• •• " + digits.substring(digits.length() - 2);
    }

    private static String doctor(Practitioner p) {
        return ("Dr " + p.getFirstName() + " " + p.getLastName()).trim();
    }

    private static boolean thisMonth(Instant at) {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        LocalDate d = at.atZone(ZoneOffset.UTC).toLocalDate();
        return d.getYear() == now.getYear() && d.getMonth() == now.getMonth();
    }

    private static ProPayout payoutRow(Payout p) {
        return new ProPayout(p.getId(), p.getPeriodEnd(), p.getCurrency(), p.getGrossMinor(), p.getCommissionMinor(),
                p.getAmountMinor(), p.getStatus(), p.getPaidAt(), p.getReference());
    }

    private static ApiException unauthenticated() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "NON_AUTHENTIFIE", "Authentification requise.");
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "RECU_INTROUVABLE", "Reçu introuvable.");
    }
}
