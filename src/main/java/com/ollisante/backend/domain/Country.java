package com.ollisante.backend.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "countries")
@Getter
@Setter
@NoArgsConstructor
public class Country {

    @Id
    private String code;

    private String nameFr;

    private String nameEn;

    private String currency;

    private String dialCode;

    private Long defaultFeeMinor;

    private String timezone;

    /** Pays proposé dans l'application (les pays désactivés restent en base pour l'historique). */
    private boolean active = true;

    /** Paiement Mobile Money proposé à la réservation (si le pays est ouvert dans PawaPay). */
    private boolean onlinePayment;

    /** Paiement sur place, le jour du rendez-vous, proposé à la réservation. */
    private boolean onSitePayment = true;

    /** Numéro à appeler en cas d'urgence médicale (affiché par le bouton « Urgence »), vérifié par l'administrateur. */
    private String emergencyNumber;
}
