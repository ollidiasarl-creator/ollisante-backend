package com.ollisante.backend.dto;

import com.ollisante.backend.domain.Practitioner;
import com.ollisante.backend.domain.Specialty;
import com.ollisante.backend.domain.UserStatus;
import com.ollisante.backend.domain.VerificationStatus;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record AdminPractitionerResponse(UUID id, UUID userId, UserStatus userStatus, String firstName, String lastName, String email, String phone,
                                        String countryCode, String licenseNumber,
                                        List<SpecialtyResponse> specialties,
                                        VerificationStatus verificationStatus) {

    public static AdminPractitionerResponse from(Practitioner p) {
        return new AdminPractitionerResponse(p.getId(), p.getUser().getId(), p.getUser().getStatus(), p.getFirstName(), p.getLastName(),
                p.getUser().getEmail(), p.getUser().getPhone(), p.getCountryCode(), p.getLicenseNumber(),
                p.getSpecialties().stream().sorted(Comparator.comparing(Specialty::getCode))
                        .map(SpecialtyResponse::from).toList(),
                p.getVerificationStatus());
    }
}
