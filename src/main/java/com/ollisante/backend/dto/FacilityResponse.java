package com.ollisante.backend.dto;

import com.ollisante.backend.domain.Facility;
import com.ollisante.backend.domain.OrganizationType;
import java.math.BigDecimal;
import java.util.UUID;

public record FacilityResponse(UUID id, String name, String countryCode, String city, String address,
                               String phone, BigDecimal latitude, BigDecimal longitude, UUID organizationId,
                               OrganizationType organizationType) {

    public static FacilityResponse from(Facility f) {
        return from(f, null);
    }

    /** {@code type} : type de la structure propriétaire du site, s'il y en a une. */
    public static FacilityResponse from(Facility f, OrganizationType type) {
        return new FacilityResponse(f.getId(), f.getName(), f.getCountryCode(), f.getCity(), f.getAddress(),
                f.getPhone(), f.getLatitude(), f.getLongitude(), f.getOrganizationId(), type);
    }
}
