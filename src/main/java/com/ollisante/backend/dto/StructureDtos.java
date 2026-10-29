package com.ollisante.backend.dto;

import com.ollisante.backend.domain.Organization;
import com.ollisante.backend.domain.OrganizationMember;
import com.ollisante.backend.domain.OrganizationType;
import com.ollisante.backend.domain.Practitioner;
import com.ollisante.backend.domain.Specialty;
import com.ollisante.backend.domain.UserStatus;
import com.ollisante.backend.domain.VerificationStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Structures (personnes morales) : inscription, profil, médecins rattachés. */
public final class StructureDtos {

    private StructureDtos() {
    }

    public record RegisterStructureRequest(
            @NotNull OrganizationType type,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(min = 2, max = 2) String countryCode,
            @Size(max = 100) String city,
            @Size(max = 255) String address,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Numéro au format international, ex. +221330000000")
            String phone,
            @Size(max = 255) @Pattern(regexp = "^$|^[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", message = "Nom de domaine invalide, ex. hopital.sn")
            String domain,
            @NotBlank @Size(max = 100) String registrationNumber,
            @NotBlank @Size(max = 200) String contactName,
            @Size(max = 100) String contactRole,
            @Min(0) @Max(100000) Integer beds,
            @NotBlank @Size(min = 8, max = 72, message = "Le mot de passe doit contenir de 8 à 72 caractères")
            String password,
            @Pattern(regexp = "fr|en", message = "Langue acceptée : fr ou en") String locale) {
    }

    /** Structure ajoutée par l'administrateur : mêmes informations que l'inscription, sans mot de passe (e-mail de bienvenue). */
    public record AdminStructureCreate(
            @NotNull OrganizationType type,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(min = 2, max = 2) String countryCode,
            @Size(max = 100) String city,
            @Size(max = 255) String address,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Numéro au format international, ex. +221330000000")
            String phone,
            @Size(max = 255) @Pattern(regexp = "^$|^[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", message = "Nom de domaine invalide, ex. hopital.sn")
            String domain,
            @NotBlank @Size(max = 100) String registrationNumber,
            @NotBlank @Size(max = 200) String contactName,
            @Size(max = 100) String contactRole,
            @Min(0) @Max(100000) Integer beds,
            @Pattern(regexp = "fr|en", message = "Langue acceptée : fr ou en") String locale) {

        public RegisterStructureRequest withPassword(String password) {
            return new RegisterStructureRequest(type, name, countryCode, city, address, email, phone, domain,
                    registrationNumber, contactName, contactRole, beds, password, locale);
        }
    }

    /** welcomeEmailSent : false si l'e-mail de bienvenue n'a pas pu partir (le compte est créé quand même). */
    public record StructureCreated(StructureProfile structure, boolean welcomeEmailSent) {
    }

    public record StructureProfile(UUID id, OrganizationType type, String name, String countryCode, String city,
                                   String address, String email, String phone, String domain,
                                   String registrationNumber, String contactName, String contactRole, Integer beds,
                                   VerificationStatus verificationStatus, Integer commissionPercent,
                                   UserStatus accountStatus, Instant createdAt) {
        public static StructureProfile from(Organization o) {
            return new StructureProfile(o.getId(), o.getType(), o.getName(), o.getCountryCode(), o.getCity(),
                    o.getAddress(), o.getEmail(), o.getPhone(), o.getDomain(), o.getRegistrationNumber(),
                    o.getContactName(), o.getContactRole(), o.getBeds(), o.getVerificationStatus(),
                    o.getCommissionPercent(), o.getUser().getStatus(), o.getCreatedAt());
        }
    }

    public record ProfileUpdate(
            @Size(max = 100) String city,
            @Size(max = 255) String address,
            @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Numéro au format international, ex. +221330000000")
            String phone,
            @Size(max = 255) @Pattern(regexp = "^$|^[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", message = "Nom de domaine invalide")
            String domain,
            @Size(max = 200) String contactName,
            @Size(max = 100) String contactRole,
            @Min(0) @Max(100000) Integer beds) {
    }

    public record MemberRow(UUID practitionerId, String firstName, String lastName, String email, String phone,
                            List<SpecialtyResponse> specialties, VerificationStatus verificationStatus,
                            OrganizationMember.Status status, Instant since) {
        public static MemberRow from(OrganizationMember m) {
            Practitioner p = m.getPractitioner();
            return new MemberRow(p.getId(), p.getFirstName(), p.getLastName(), p.getUser().getEmail(),
                    p.getUser().getPhone(),
                    p.getSpecialties().stream().sorted(Comparator.comparing(Specialty::getCode))
                            .map(SpecialtyResponse::from).toList(),
                    p.getVerificationStatus(), m.getStatus(), m.getCreatedAt());
        }
    }

    public record InviteRequest(@NotBlank @Email @Size(max = 255) String email) {
    }

    /** Invitation ou rattachement vu par le médecin dans l'application. */
    public record Membership(UUID organizationId, String name, OrganizationType type, String city,
                             OrganizationMember.Status status) {
        public static Membership from(OrganizationMember m) {
            Organization o = m.getOrganization();
            return new Membership(o.getId(), o.getName(), o.getType(), o.getCity(), m.getStatus());
        }
    }

    public record CommissionUpdate(@Min(0) @Max(100) Integer commissionPercent) {
    }
}
