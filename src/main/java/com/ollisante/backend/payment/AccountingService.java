package com.ollisante.backend.payment;

import com.ollisante.backend.common.ApiException;
import com.ollisante.backend.domain.Appointment;
import com.ollisante.backend.domain.Organization;
import com.ollisante.backend.domain.Payment;
import com.ollisante.backend.domain.PaymentStatus;
import com.ollisante.backend.domain.Payout;
import com.ollisante.backend.domain.Practitioner;
import com.ollisante.backend.domain.Refund;
import com.ollisante.backend.dto.PageResponse;
import com.ollisante.backend.repository.OrganizationRepository;
import com.ollisante.backend.repository.PaymentRepository;
import com.ollisante.backend.repository.PayoutRepository;
import com.ollisante.backend.repository.RefundRepository;
import com.ollisante.backend.service.AuditService;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Comptabilité : encaissements, remboursements, commission OLIDIA et reversements hebdomadaires (manuels). */
@Service
@RequiredArgsConstructor
public class AccountingService {

    private static final Set<PaymentStatus> COLLECTED =
            Set.of(PaymentStatus.SUCCEEDED, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED);
    private static final Set<PaymentStatus> PAYABLE = Set.of(PaymentStatus.SUCCEEDED, PaymentStatus.PARTIALLY_REFUNDED);

    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final PayoutRepository payouts;
    private final AuditService audit;
    private final OrganizationRepository organizations;

    @Value("${ollisante.payments.commission-percent:15}")
    private int commissionPercent;

    @Value("${ollisante.payments.structure-commission-percent:10}")
    private int structureCommissionPercent;

    public record CurrencyTotals(String currency, long payments, long collectedMinor, long refundedMinor,
                                 long netMinor, long commissionMinor, long practitionersMinor) {
    }

    public record Summary(String from, String to, int commissionPercent, int structureCommissionPercent,
                          List<CurrencyTotals> totals,
                          List<CurrencyTotals> dueToPractitioners, long failedRefunds) {
    }

    public record PaymentRow(UUID id, Instant createdAt, String patient, String practitioner, Instant appointmentAt,
                             long amountMinor, long refundedMinor, String currency, PaymentStatus status,
                             String operator, String failureCode, boolean paidOut) {
    }

    public record RefundRow(UUID id, UUID paymentId, Instant createdAt, String patient, long amountMinor,
                            String currency, String reason, Refund.Status status, String failureCode) {
    }

    public record PayoutRow(UUID id, String practitioner, String practitionerEmail, String practitionerPhone,
                            String currency, long grossMinor, long commissionMinor, long amountMinor,
                            LocalDate periodEnd, Payout.Status status, Instant paidAt, String reference) {
        static PayoutRow from(Payout p) {
            Practitioner pr = p.getPractitioner();
            return new PayoutRow(p.getId(), pr.getFirstName() + " " + pr.getLastName(), pr.getUser().getEmail(),
                    pr.getUser().getPhone(), p.getCurrency(), p.getGrossMinor(), p.getCommissionMinor(),
                    p.getAmountMinor(), p.getPeriodEnd(), p.getStatus(), p.getPaidAt(), p.getReference());
        }
    }

    // ---------- synthèse ----------

    @Transactional(readOnly = true)
    public Summary summary(LocalDate from, LocalDate to) {
        Instant start = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<CurrencyTotals> totals = new ArrayList<>();
        Map<String, long[]> byCurrency = new LinkedHashMap<>();
        for (Payment p : payments.collectedBetween(COLLECTED, start, end)) {
            long[] t = byCurrency.computeIfAbsent(p.getCurrency(), c -> new long[4]);
            long net = p.getAmountMinor() - p.getRefundedMinor();
            t[0]++;
            t[1] += p.getAmountMinor();
            t[2] += p.getRefundedMinor();
            t[3] += commission(net, rateFor(p));
        }
        byCurrency.forEach((c, t) -> totals.add(new CurrencyTotals(c, t[0], t[1], t[2], t[1] - t[2], t[3],
                t[1] - t[2] - t[3])));
        Map<String, long[]> due = new LinkedHashMap<>();
        for (Payout p : payouts.findByStatusOrderByPeriodEndDescCreatedAtDesc(Payout.Status.DUE,
                PageRequest.of(0, 1000)).getContent()) {
            long[] t = due.computeIfAbsent(p.getCurrency(), c -> new long[4]);
            t[0]++;
            t[1] += p.getGrossMinor();
            t[2] += p.getCommissionMinor();
            t[3] += p.getAmountMinor();
        }
        List<CurrencyTotals> dueRows = new ArrayList<>();
        due.forEach((c, t) -> dueRows.add(new CurrencyTotals(c, t[0], t[1], 0, t[1], t[2], t[3])));
        long failed = refunds.findByStatus(Refund.Status.FAILED).size();
        return new Summary(from.toString(), to.toString(), commissionPercent, structureCommissionPercent, totals,
                dueRows, failed);
    }

    /** 15 % pour un médecin indépendant ; taux de la structure (10 % par défaut) pour un rendez-vous pris chez elle. */
    /** Commission d'un médecin indépendant. */
    public int practitionerRate() {
        return commissionPercent;
    }

    /** Commission appliquée aux rendez-vous pris chez une structure (son taux propre, sinon le taux par défaut). */
    public int structureRate(Organization org) {
        return org.getCommissionPercent() != null ? org.getCommissionPercent() : structureCommissionPercent;
    }

    public int rateFor(Payment p) {
        UUID orgId = p.getAppointment().getOrganizationId();
        if (orgId == null) return commissionPercent;
        Integer custom = organizations.findById(orgId).map(Organization::getCommissionPercent).orElse(null);
        return custom != null ? custom : structureCommissionPercent;
    }

    private static long commission(long netMinor, int percent) {
        return Math.round(netMinor * percent / 100.0);
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentRow> payments(int page, int size) {
        return PageResponse.of(payments.findAllByOrderByCreatedAtDesc(
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))).map(p -> {
            Appointment a = p.getAppointment();
            return new PaymentRow(p.getId(), p.getCreatedAt(),
                    a.getPatient().getFirstName() + " " + a.getPatient().getLastName(),
                    a.getPractitioner().getFirstName() + " " + a.getPractitioner().getLastName(), a.getStartsAt(),
                    p.getAmountMinor(), p.getRefundedMinor(), p.getCurrency(), p.getStatus(), p.getOperator(),
                    p.getFailureCode(), p.getPayoutId() != null);
        }));
    }

    /** Remboursements refusés par PawaPay : à régler à la main avec le patient. */
    @Transactional(readOnly = true)
    public List<RefundRow> failedRefunds() {
        return refunds.findByStatus(Refund.Status.FAILED).stream().map(r -> {
            Appointment a = r.getPayment().getAppointment();
            return new RefundRow(r.getId(), r.getPayment().getId(), r.getCreatedAt(),
                    a.getPatient().getFirstName() + " " + a.getPatient().getLastName(), r.getAmountMinor(),
                    r.getCurrency(), r.getReason(), r.getStatus(), r.getFailureCode());
        }).toList();
    }

    // ---------- reversements ----------

    /** Dernier vendredi (aujourd'hui compris) : les relevés couvrent les consultations antérieures à ce jour. */
    public static LocalDate lastFriday(LocalDate today) {
        return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY));
    }

    /**
     * Prépare les relevés : pour chaque praticien et devise, les paiements de consultations passées avant
     * {@code periodEnd} et pas encore reversés. Sans doublon : chaque paiement n'entre que dans un relevé.
     */
    @Transactional
    public List<PayoutRow> generatePayouts(UUID adminUserId, LocalDate periodEnd) {
        Instant before = periodEnd.atStartOfDay(ZoneOffset.UTC).toInstant();
        Map<String, List<Payment>> groups = new LinkedHashMap<>();
        for (Payment p : payments.eligibleForPayout(PAYABLE, before)) {
            String key = p.getAppointment().getPractitioner().getId() + "|" + p.getCurrency();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
        }
        List<PayoutRow> created = new ArrayList<>();
        for (List<Payment> group : groups.values()) {
            long gross = group.stream().mapToLong(p -> p.getAmountMinor() - p.getRefundedMinor()).sum();
            if (gross <= 0) continue;
            long commission = group.stream().mapToLong(p -> commission(p.getAmountMinor() - p.getRefundedMinor(),
                    rateFor(p))).sum();
            Payout payout = new Payout();
            payout.setPractitioner(group.get(0).getAppointment().getPractitioner());
            payout.setCurrency(group.get(0).getCurrency());
            payout.setGrossMinor(gross);
            payout.setCommissionMinor(commission);
            payout.setAmountMinor(gross - payout.getCommissionMinor());
            payout.setPeriodEnd(periodEnd);
            payouts.save(payout);
            group.forEach(p -> p.setPayoutId(payout.getId()));
            created.add(PayoutRow.from(payout));
        }
        if (adminUserId != null && !created.isEmpty()) {
            audit.record(adminUserId, "RELEVE_PREPARE", "PAYOUT", periodEnd, created.size() + " relevé(s)");
        }
        return created;
    }

    @Transactional(readOnly = true)
    public PageResponse<PayoutRow> payouts(Payout.Status status, int page, int size) {
        PageRequest req = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200));
        return PageResponse.of((status == null ? payouts.findAllByOrderByPeriodEndDescCreatedAtDesc(req)
                : payouts.findByStatusOrderByPeriodEndDescCreatedAtDesc(status, req)).map(PayoutRow::from));
    }

    @Transactional
    public PayoutRow markPaid(UUID adminUserId, UUID payoutId, String reference) {
        Payout p = payouts.findById(payoutId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RELEVE_INTROUVABLE", "Relevé introuvable."));
        if (p.getStatus() == Payout.Status.PAID) {
            throw new ApiException(HttpStatus.CONFLICT, "RELEVE_DEJA_PAYE", "Ce relevé est déjà marqué payé.");
        }
        p.setStatus(Payout.Status.PAID);
        p.setPaidAt(Instant.now());
        p.setReference(reference == null || reference.isBlank() ? null : reference.trim());
        audit.record(adminUserId, "REVERSEMENT_PAYE", "PAYOUT", payoutId,
                p.getPractitioner().getFirstName() + " " + p.getPractitioner().getLastName() + " : "
                        + Money.toApi(p.getAmountMinor(), p.getCurrency()) + " " + p.getCurrency());
        return PayoutRow.from(p);
    }
}
