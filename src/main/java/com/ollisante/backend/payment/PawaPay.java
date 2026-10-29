package com.ollisante.backend.payment;

import java.util.List;
import java.util.UUID;

/** Accès à PawaPay (Mobile Money). Montants au format attendu par l'API : chaîne décimale, ex. "500" ou "10.50". */
public interface PawaPay {

    /** Faux si aucune clé n'est configurée : le paiement en ligne est alors indisponible. */
    boolean enabled();

    /** ACCEPTED, REJECTED ou DUPLICATE_IGNORED ; failureCode renseigné en cas de rejet. */
    record Initiation(String status, String failureCode) {
        public boolean accepted() {
            return "ACCEPTED".equals(status) || "DUPLICATE_IGNORED".equals(status);
        }
    }

    /** status : COMPLETED, FAILED, NOT_FOUND ou une étape intermédiaire (ACCEPTED, PROCESSING…). */
    record Status(String status, String failureCode, String providerTransactionId) {
    }

    /** Opérateur disponible pour les paiements dans un pays. */
    record Operator(String code, String name, String currency, String minAmount, String maxAmount, boolean decimals) {
    }

    Initiation deposit(UUID depositId, String phone, String operator, String amount, String currency, String message);

    Status depositStatus(UUID depositId);

    Initiation refund(UUID refundId, UUID depositId, String amount, String currency);

    Status refundStatus(UUID refundId);

    /** Opérateurs ouverts aux dépôts pour un pays (code ISO 3166 alpha-3, ex. SEN). */
    List<Operator> operators(String countryIso3);
}
