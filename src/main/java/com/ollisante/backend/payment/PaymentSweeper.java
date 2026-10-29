package com.ollisante.backend.payment;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Filet de sécurité si un rappel de PawaPay se perd : contrôle chaque minute les paiements et remboursements en attente. */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentSweeper {

    private final PaymentService service;
    private final PawaPay pawaPay;

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT30S")
    public void run() {
        if (!pawaPay.enabled()) return;
        for (UUID id : service.pendingPaymentIds()) {
            try {
                service.refresh(id);
            } catch (RuntimeException e) {
                log.warn("Contrôle du paiement {} impossible : {}", id, e.getMessage());
            }
        }
        for (UUID id : service.pendingRefundIds()) {
            try {
                service.refreshRefund(id);
            } catch (RuntimeException e) {
                log.warn("Contrôle du remboursement {} impossible : {}", id, e.getMessage());
            }
        }
    }
}
