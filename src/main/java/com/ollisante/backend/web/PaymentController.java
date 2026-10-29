package com.ollisante.backend.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.ollisante.backend.domain.Payout;
import com.ollisante.backend.dto.PageResponse;
import com.ollisante.backend.payment.AccountingService;
import com.ollisante.backend.payment.AccountingService.PaymentRow;
import com.ollisante.backend.payment.AccountingService.PayoutRow;
import com.ollisante.backend.payment.AccountingService.RefundRow;
import com.ollisante.backend.payment.AccountingService.Summary;
import com.ollisante.backend.payment.PawaPay;
import com.ollisante.backend.payment.PaymentService;
import com.ollisante.backend.payment.PaymentService.PaymentView;
import com.ollisante.backend.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService payments;
    private final AccountingService accounting;

    public record PayRequest(@NotBlank @Size(max = 25) String phone, @NotBlank @Size(max = 40) String operator) {
    }

    public record MarkPaidRequest(@Size(max = 100) String reference) {
    }

    // ---------- patients ----------

    @GetMapping("/payments/operators")
    public List<PawaPay.Operator> operators(@RequestParam String countryCode) {
        return payments.operators(countryCode.toUpperCase(Locale.ROOT));
    }

    @PostMapping("/appointments/{id}/payments")
    @PreAuthorize("hasRole('PATIENT')")
    public PaymentView pay(Authentication auth, @PathVariable UUID id, @Valid @RequestBody PayRequest r) {
        return payments.pay(CurrentUser.id(auth), id, r.phone(), r.operator());
    }

    @GetMapping("/payments/{id}")
    @PreAuthorize("hasRole('PATIENT')")
    public PaymentView status(Authentication auth, @PathVariable UUID id) {
        return payments.status(CurrentUser.id(auth), id);
    }

    /**
     * Adresse de rappel à configurer chez PawaPay (dépôts et remboursements). Publique : son contenu n'est pas
     * cru sur parole, le statut est redemandé à PawaPay. Répond toujours 200 pour arrêter les relances.
     */
    @PostMapping("/payments/pawapay/callback")
    public ResponseEntity<Void> callback(@RequestBody(required = false) JsonNode body) {
        try {
            UUID depositId = uuid(body, "depositId");
            UUID refundId = uuid(body, "refundId");
            payments.callback(depositId, refundId);
        } catch (RuntimeException e) {
            log.warn("Rappel PawaPay non traité : {}", e.getMessage()); // le balayage périodique rattrapera
        }
        return ResponseEntity.ok().build();
    }

    private static UUID uuid(JsonNode body, String field) {
        if (body == null || !body.hasNonNull(field)) return null;
        try {
            return UUID.fromString(body.get(field).asText());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---------- comptabilité (ADMIN, règle /api/v1/admin/** dans SecurityConfig) ----------

    @GetMapping("/admin/accounting/summary")
    public Summary summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return accounting.summary(from != null ? from : today.withDayOfMonth(1), to != null ? to : today);
    }

    @GetMapping("/admin/accounting/payments")
    public PageResponse<PaymentRow> paymentList(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "50") int size) {
        return accounting.payments(page, size);
    }

    @GetMapping("/admin/accounting/refunds/failed")
    public List<RefundRow> failedRefunds() {
        return accounting.failedRefunds();
    }

    @GetMapping("/admin/accounting/payouts")
    public PageResponse<PayoutRow> payouts(@RequestParam(required = false) Payout.Status status,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "100") int size) {
        return accounting.payouts(status, page, size);
    }

    @PostMapping("/admin/accounting/payouts/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public List<PayoutRow> generate(Authentication auth) {
        return accounting.generatePayouts(CurrentUser.id(auth),
                AccountingService.lastFriday(LocalDate.now(ZoneOffset.UTC)));
    }

    @PostMapping("/admin/accounting/payouts/{id}/paid")
    public PayoutRow markPaid(Authentication auth, @PathVariable UUID id, @Valid @RequestBody MarkPaidRequest r) {
        return accounting.markPaid(CurrentUser.id(auth), id, r.reference());
    }
}
