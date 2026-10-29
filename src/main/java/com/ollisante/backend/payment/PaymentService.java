package com.ollisante.backend.payment;

import com.ollisante.backend.common.ApiException;
import com.ollisante.backend.domain.Appointment;
import com.ollisante.backend.domain.AppointmentStatus;
import com.ollisante.backend.domain.Country;
import com.ollisante.backend.domain.Payment;
import com.ollisante.backend.domain.PaymentMode;
import com.ollisante.backend.domain.PaymentStatus;
import com.ollisante.backend.domain.Refund;
import com.ollisante.backend.domain.Role;
import com.ollisante.backend.repository.AppointmentRepository;
import com.ollisante.backend.repository.CountryRepository;
import com.ollisante.backend.repository.PatientRepository;
import com.ollisante.backend.repository.PaymentRepository;
import com.ollisante.backend.repository.RefundRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Paiement Mobile Money des rendez-vous via PawaPay. Le statut d'un paiement n'est jamais pris dans un rappel :
 * il est toujours redemandé à PawaPay (rappel, contrôle de l'application et balayage périodique font de même).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private static final Duration OPERATORS_CACHE = Duration.ofMinutes(10);
    private static final Duration PAYMENT_RETRY_AFTER = Duration.ofMinutes(3);

    private final PawaPay pawaPay;
    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final AppointmentRepository appointments;
    private final PatientRepository patients;
    private final CountryRepository countries;
    private final TransactionTemplate tx;
    private final Map<String, CachedOperators> operatorsCache = new ConcurrentHashMap<>();

    private record CachedOperators(Instant at, List<PawaPay.Operator> list) {
    }

    public record PaymentView(UUID id, UUID appointmentId, PaymentStatus status, long amountMinor, String currency,
                              String operator, String failureCode, AppointmentStatus appointmentStatus) {
        static PaymentView from(Payment p) {
            return new PaymentView(p.getId(), p.getAppointment().getId(), p.getStatus(), p.getAmountMinor(),
                    p.getCurrency(), p.getOperator(), p.getFailureCode(), p.getAppointment().getStatus());
        }
    }

    public record CancellationPreview(boolean paid, int refundPercent, long refundMinor, String currency) {
    }

    // ---------- opérateurs ----------

    /** Opérateurs Mobile Money ouverts dans ce pays, dans la devise du tarif (mis en cache 10 min). */
    public List<PawaPay.Operator> operators(String countryCode) {
        Country c = countries.findById(countryCode).orElse(null);
        if (c == null || !c.isOnlinePayment() || !pawaPay.enabled()) return List.of();
        CachedOperators cached = operatorsCache.get(c.getCode());
        if (cached == null || cached.at().isBefore(Instant.now().minus(OPERATORS_CACHE))) {
            List<PawaPay.Operator> list = pawaPay.operators(Money.iso3(c.getCode())).stream()
                    .filter(o -> o.currency().equals(c.getCurrency())).toList();
            cached = new CachedOperators(Instant.now(), list);
            operatorsCache.put(c.getCode(), cached);
        }
        return cached.list();
    }

    // ---------- paiement ----------

    /** Lance le paiement : le patient reçoit une demande de validation (code secret) sur son téléphone. */
    public PaymentView pay(UUID userId, UUID appointmentId, String rawPhone, String operator) {
        Payment created = tx.execute(status -> {
            Appointment a = ownAppointment(userId, appointmentId);
            if (a.getPaymentMode() != PaymentMode.ONLINE || a.getStatus() != AppointmentStatus.PENDING_PAYMENT) {
                throw new ApiException(HttpStatus.CONFLICT, "PAIEMENT_INUTILE", "Ce rendez-vous n'est pas à payer.");
            }
            Instant recent = Instant.now().minus(PAYMENT_RETRY_AFTER);
            for (Payment p : payments.findByAppointmentIdOrderByCreatedAtDesc(a.getId())) {
                if (p.getStatus() == PaymentStatus.PENDING && p.getCreatedAt().isAfter(recent)) {
                    throw new ApiException(HttpStatus.CONFLICT, "PAIEMENT_EN_COURS",
                            "Un paiement est déjà en cours : validez-le sur votre téléphone.");
                }
            }
            String countryCode = a.getPractitioner().getCountryCode();
            if (operators(countryCode).stream().noneMatch(o -> o.code().equals(operator))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "OPERATEUR_INDISPONIBLE",
                        "Cet opérateur n'est pas disponible pour ce pays.");
            }
            String dial = countries.findById(countryCode).map(Country::getDialCode).orElse("+").substring(1);
            String phone = msisdn(rawPhone, dial);

            Payment p = new Payment();
            p.setId(UUID.randomUUID());
            p.setAppointment(a);
            p.setPayerUserId(userId);
            p.setAmountMinor(a.getPriceMinor());
            p.setCurrency(a.getPriceCurrency());
            p.setOperator(operator);
            p.setPhone(phone);
            p.setStatus(PaymentStatus.PENDING);
            return payments.saveAndFlush(p);
        });

        // Appel à PawaPay hors transaction : le paiement est déjà enregistré si un rappel arrive très vite.
        PawaPay.Initiation init;
        try {
            init = pawaPay.deposit(created.getId(), created.getPhone(), created.getOperator(),
                    Money.toApi(created.getAmountMinor(), created.getCurrency()), created.getCurrency(),
                    "Ollisante consultation");
        } catch (RuntimeException e) {
            log.error("PawaPay injoignable pour le paiement {}", created.getId(), e);
            init = new PawaPay.Initiation("REJECTED", "PAWAPAY_INJOIGNABLE");
        }
        PawaPay.Initiation result = init;
        return tx.execute(status -> {
            Payment p = payments.lockById(created.getId()).orElseThrow();
            if (!result.accepted() && p.getStatus() == PaymentStatus.PENDING) {
                p.setStatus(PaymentStatus.FAILED);
                p.setFailureCode(result.failureCode() == null ? "REJETE" : result.failureCode());
            }
            return PaymentView.from(p);
        });
    }

    /** Statut vu par le patient ; redemande à PawaPay tant que le paiement est en attente. */
    public PaymentView status(UUID userId, UUID paymentId) {
        UUID owner = tx.execute(s -> payments.findById(paymentId).map(Payment::getPayerUserId).orElse(null));
        if (!userId.equals(owner)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PAIEMENT_INTROUVABLE", "Paiement introuvable.");
        }
        return refresh(paymentId);
    }

    /** Met à jour un paiement en attente d'après PawaPay (idempotent : rappels en double sans effet). */
    public PaymentView refresh(UUID paymentId) {
        return tx.execute(status -> doRefresh(paymentId));
    }

    private PaymentView doRefresh(UUID paymentId) {
        Payment p = payments.lockById(paymentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAIEMENT_INTROUVABLE", "Paiement introuvable."));
        if (p.getStatus() != PaymentStatus.PENDING) return PaymentView.from(p);
        PawaPay.Status s = pawaPay.depositStatus(p.getId());
        switch (s.status()) {
            case "COMPLETED" -> succeed(p, s.providerTransactionId());
            case "FAILED" -> {
                p.setStatus(PaymentStatus.FAILED);
                p.setFailureCode(s.failureCode());
            }
            case "NOT_FOUND" -> {
                if (p.getCreatedAt().isBefore(Instant.now().minus(Duration.ofMinutes(10)))) {
                    p.setStatus(PaymentStatus.FAILED);
                    p.setFailureCode("INCONNU_CHEZ_PAWAPAY");
                }
            }
            default -> { } // ACCEPTED, PROCESSING, IN_RECONCILIATION : on attend
        }
        return PaymentView.from(p);
    }

    private void succeed(Payment p, String providerReference) {
        p.setStatus(PaymentStatus.SUCCEEDED);
        p.setProviderReference(providerReference);
        Appointment a = p.getAppointment();
        if (a.getStatus() == AppointmentStatus.PENDING_PAYMENT) {
            a.setStatus(AppointmentStatus.CONFIRMED);
        } else {
            // Rendez-vous annulé entre-temps (délai de paiement dépassé ou annulation) : on rembourse tout.
            startRefund(p, p.getAmountMinor(), "PAIEMENT_TARDIF");
        }
    }

    // ---------- annulation et remboursements ----------

    /**
     * Règles : annulation par le praticien ou l'administration = 100 % ; par le patient : 100 % jusqu'à 24 h avant,
     * 50 % entre 24 h et 6 h, rien ensuite (absence comprise).
     */
    public static int refundPercent(Role cancelledBy, Instant startsAt, Instant now) {
        if (cancelledBy != Role.PATIENT) return 100;
        long minutes = Duration.between(now, startsAt).toMinutes();
        if (minutes >= 24 * 60) return 100;
        if (minutes >= 6 * 60) return 50;
        return 0;
    }

    @Transactional(readOnly = true)
    public CancellationPreview preview(Appointment a, Role role) {
        Payment paid = succeeded(a.getId());
        int pct = refundPercent(role, a.getStartsAt(), Instant.now());
        if (paid == null) return new CancellationPreview(false, pct, 0, a.getPriceCurrency());
        return new CancellationPreview(true, pct, Money.percent(paid.getAmountMinor(), pct), paid.getCurrency());
    }

    /** Appelé dans la transaction d'annulation du rendez-vous. */
    @Transactional
    public void refundForCancellation(Appointment a, Role role) {
        Payment paid = succeeded(a.getId());
        if (paid == null) return;
        long amount = Money.percent(paid.getAmountMinor(), refundPercent(role, a.getStartsAt(), Instant.now()));
        if (amount > 0) {
            startRefund(paid, amount, role == Role.PATIENT ? "ANNULATION_PATIENT" : "ANNULATION_" + role.name());
        }
    }

    private Payment succeeded(UUID appointmentId) {
        return payments.findByAppointmentIdOrderByCreatedAtDesc(appointmentId).stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCEEDED).findFirst().orElse(null);
    }

    private void startRefund(Payment p, long amount, String reason) {
        Refund r = new Refund();
        r.setId(UUID.randomUUID());
        r.setPayment(p);
        r.setAmountMinor(amount);
        r.setCurrency(p.getCurrency());
        r.setReason(reason);
        r.setStatus(Refund.Status.PENDING);
        refunds.saveAndFlush(r);
        PawaPay.Initiation init;
        try {
            init = pawaPay.refund(r.getId(), p.getId(), Money.toApi(amount, p.getCurrency()), p.getCurrency());
        } catch (RuntimeException e) {
            log.error("PawaPay injoignable pour le remboursement {}", r.getId(), e);
            init = new PawaPay.Initiation("REJECTED", "PAWAPAY_INJOIGNABLE");
        }
        if (!init.accepted()) {
            // Visible dans la comptabilité : à traiter à la main.
            r.setStatus(Refund.Status.FAILED);
            r.setFailureCode(init.failureCode() == null ? "REJETE" : init.failureCode());
            log.warn("Remboursement {} refusé par PawaPay : {}", r.getId(), r.getFailureCode());
        }
    }

    public void refreshRefund(UUID refundId) {
        tx.executeWithoutResult(status -> doRefreshRefund(refundId));
    }

    private void doRefreshRefund(UUID refundId) {
        Refund r = refunds.lockById(refundId).orElse(null);
        if (r == null || r.getStatus() != Refund.Status.PENDING) return;
        PawaPay.Status s = pawaPay.refundStatus(r.getId());
        if ("COMPLETED".equals(s.status())) {
            r.setStatus(Refund.Status.SUCCEEDED);
            Payment p = payments.lockById(r.getPayment().getId()).orElseThrow();
            p.setRefundedMinor(p.getRefundedMinor() + r.getAmountMinor());
            p.setStatus(p.getRefundedMinor() >= p.getAmountMinor() ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
        } else if ("FAILED".equals(s.status())) {
            r.setStatus(Refund.Status.FAILED);
            r.setFailureCode(s.failureCode());
        }
    }

    /** Retour de PawaPay (adresse de rappel) : seul l'identifiant est utilisé, le statut est redemandé. */
    public void callback(UUID depositId, UUID refundId) {
        if (depositId != null && Boolean.TRUE.equals(tx.execute(s -> payments.existsById(depositId)))) refresh(depositId);
        if (refundId != null) refreshRefund(refundId);
    }

    @Transactional(readOnly = true)
    public List<UUID> pendingPaymentIds() {
        Instant now = Instant.now();
        return payments.findByStatusAndCreatedAtBetween(PaymentStatus.PENDING, now.minus(Duration.ofDays(1)),
                now.minusSeconds(20)).stream().map(Payment::getId).toList();
    }

    @Transactional(readOnly = true)
    public List<UUID> pendingRefundIds() {
        return refunds.findByStatus(Refund.Status.PENDING).stream().map(Refund::getId).toList();
    }

    // ---------- outils ----------

    private Appointment ownAppointment(UUID userId, UUID appointmentId) {
        UUID patientId = patients.findByUserId(userId).map(x -> x.getId()).orElse(null);
        return appointments.findById(appointmentId)
                .filter(a -> a.getPatient().getId().equals(patientId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RDV_INTROUVABLE", "Rendez-vous introuvable."));
    }

    private static final Set<Character> SEPARATORS = Set.of(' ', '-', '.', '(', ')');

    /** Numéro au format PawaPay (chiffres seuls, indicatif compris, sans + ni 0 initial). */
    static String msisdn(String raw, String dialDigits) {
        if (raw == null) throw invalidPhone();
        StringBuilder digits = new StringBuilder();
        String s = raw.trim();
        boolean international = s.startsWith("+") || s.startsWith("00");
        for (char c : (s.startsWith("00") ? s.substring(2) : s).toCharArray()) {
            if (Character.isDigit(c)) digits.append(c);
            else if (c != '+' && !SEPARATORS.contains(c)) throw invalidPhone();
        }
        String d = digits.toString();
        if (!international) d = dialDigits + d.replaceFirst("^0+", "");
        if (!d.startsWith(dialDigits) || d.length() < dialDigits.length() + 6 || d.length() > 15) throw invalidPhone();
        return d;
    }

    private static ApiException invalidPhone() {
        return new ApiException(HttpStatus.BAD_REQUEST, "TELEPHONE_INVALIDE",
                "Numéro Mobile Money invalide pour ce pays.");
    }
}
