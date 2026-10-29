package com.ollisante.backend.payment;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * Développement local uniquement : simule PawaPay sans appel réseau. Un numéro qui se termine par 99 est refusé
 * (paiement non approuvé), les autres paiements réussissent au premier contrôle de statut.
 */
@Slf4j
public class SimulatedPawaPay implements PawaPay {

    private final Map<UUID, Status> deposits = new ConcurrentHashMap<>();
    private final Map<UUID, Status> refunds = new ConcurrentHashMap<>();

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public Initiation deposit(UUID depositId, String phone, String operator, String amount, String currency,
                              String message) {
        log.info("[PawaPay simulé] dépôt {} {} {} depuis {} ({})", depositId, amount, currency, phone, operator);
        deposits.put(depositId, phone.endsWith("99")
                ? new Status("FAILED", "PAYMENT_NOT_APPROVED", null)
                : new Status("COMPLETED", null, "SIM-" + depositId.toString().substring(0, 8)));
        return new Initiation("ACCEPTED", null);
    }

    @Override
    public Status depositStatus(UUID depositId) {
        return deposits.getOrDefault(depositId, new Status("NOT_FOUND", null, null));
    }

    @Override
    public Initiation refund(UUID refundId, UUID depositId, String amount, String currency) {
        log.info("[PawaPay simulé] remboursement {} de {} {} sur {}", refundId, amount, currency, depositId);
        refunds.put(refundId, new Status("COMPLETED", null, null));
        return new Initiation("ACCEPTED", null);
    }

    @Override
    public Status refundStatus(UUID refundId) {
        return refunds.getOrDefault(refundId, new Status("NOT_FOUND", null, null));
    }

    @Override
    public List<Operator> operators(String countryIso3) {
        return switch (countryIso3) {
            case "SEN" -> List.of(new Operator("ORANGE_SEN", "Orange", "XOF", "100", "1000000", false),
                    new Operator("FREE_SEN", "Free", "XOF", "100", "1000000", false));
            case "CIV" -> List.of(new Operator("MTN_MOMO_CIV", "MTN", "XOF", "100", "1000000", false),
                    new Operator("ORANGE_CIV", "Orange", "XOF", "100", "1000000", false));
            case "BEN" -> List.of(new Operator("MOOV_BEN", "Moov", "XOF", "100", "1000000", false),
                    new Operator("MTN_MOMO_BEN", "MTN", "XOF", "100", "1000000", false));
            case "COD" -> List.of(new Operator("AIRTEL_COD", "Airtel", "USD", "1", "2500", true));
            default -> List.of();
        };
    }
}
