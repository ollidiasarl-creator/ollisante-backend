package com.ollisante.backend.dto;

import com.ollisante.backend.domain.Country;
import com.ollisante.backend.domain.Practitioner;
import com.ollisante.backend.domain.Specialty;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record PractitionerSummaryResponse(
        UUID id,
        String firstName,
        String lastName,
        String countryCode,
        List<SpecialtyResponse> specialties,
        Long consultationFeeMinor,
        String feeCurrency,
        boolean teleconsultationEnabled,
        String bio) {

    /**
     * À appeler dans une transaction : les spécialités sont chargées à la demande.
     * Le tarif affiché est celui du pays du praticien (tarif fixé par l'administration).
     */
    public static PractitionerSummaryResponse from(Practitioner p, Country country) {
        List<SpecialtyResponse> specialties = p.getSpecialties().stream()
                .sorted(Comparator.comparing(Specialty::getCode))
                .map(SpecialtyResponse::from)
                .toList();
        return new PractitionerSummaryResponse(p.getId(), p.getFirstName(), p.getLastName(), p.getCountryCode(),
                specialties, country == null ? null : country.getDefaultFeeMinor(),
                country == null ? null : country.getCurrency(), p.isTeleconsultationEnabled(),
                p.getBio());
    }
}
