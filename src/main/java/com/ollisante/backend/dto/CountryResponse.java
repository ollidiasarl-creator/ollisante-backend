package com.ollisante.backend.dto;

import com.ollisante.backend.domain.Country;

/**
 * Pays proposé dans l'application et sur le site. Le tarif de consultation (fixé par l'administration, null = non
 * défini) et le mode de paiement sont publics : ils sont affichés aux patients avant toute réservation.
 */
public record CountryResponse(String code, String nameFr, String nameEn, String currency, String dialCode,
                              String emergencyNumber, Long defaultFeeMinor, boolean onlinePayment,
                              boolean onSitePayment) {

    public static CountryResponse from(Country c) {
        return new CountryResponse(c.getCode(), c.getNameFr(), c.getNameEn(), c.getCurrency(), c.getDialCode(),
                c.getEmergencyNumber(), c.getDefaultFeeMinor(), c.isOnlinePayment(), c.isOnSitePayment());
    }
}
