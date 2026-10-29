package com.ollisante.backend.dto;

import com.ollisante.backend.domain.Organization;
import com.ollisante.backend.domain.OrganizationType;
import com.ollisante.backend.domain.Patient;
import com.ollisante.backend.domain.Practitioner;
import com.ollisante.backend.domain.Relative;
import com.ollisante.backend.domain.Role;
import com.ollisante.backend.domain.Sex;
import com.ollisante.backend.domain.User;
import com.ollisante.backend.domain.VerificationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Rubrique « Compte » : profil du titulaire, modification des informations personnelles et du mot de passe. */
public final class ProfileDtos {

    private ProfileDtos() {
    }

    /** countryCode : pays du compte (filtre de tout ce qu'il voit et réserve), null s'il n'est pas encore connu. */
    public record Profile(UUID id, Role role, String email, String phone, String locale, boolean hasPassword,
                          String countryCode, boolean twoStepEnabled, boolean notifyEmail, boolean notifySms,
                          PatientInfo patient, PractitionerInfo practitioner, StructureInfo structure) {

        public static Profile of(User u, String countryCode, Patient patient, Practitioner practitioner,
                                 Organization org) {
            return new Profile(u.getId(), u.getRole(), u.getEmail(), u.getPhone(), u.getLocale(),
                    u.getPasswordHash() != null, countryCode, u.isTwoStepEnabled(), u.isNotifyEmail(), u.isNotifySms(),
                    patient == null ? null : PatientInfo.from(patient),
                    practitioner == null ? null : PractitionerInfo.from(practitioner),
                    org == null ? null : StructureInfo.from(org));
        }
    }

    public record PatientInfo(String firstName, String lastName, LocalDate birthDate, Sex sex, String countryCode,
                              String city, String address) {
        static PatientInfo from(Patient p) {
            return new PatientInfo(p.getFirstName(), p.getLastName(), p.getBirthDate(), p.getSex(), p.getCountryCode(),
                    p.getCity(), p.getAddress());
        }
    }

    public record SpecialtyInfo(String code, String nameFr, String nameEn) {
    }

    public record PractitionerInfo(String firstName, String lastName, String countryCode, String licenseNumber,
                                   String bio, List<SpecialtyInfo> specialties, VerificationStatus verificationStatus,
                                   boolean teleconsultationEnabled) {
        static PractitionerInfo from(Practitioner p) {
            List<SpecialtyInfo> specialties = p.getSpecialties().stream()
                    .sorted(Comparator.comparing(s -> s.getNameFr()))
                    .map(s -> new SpecialtyInfo(s.getCode(), s.getNameFr(), s.getNameEn()))
                    .toList();
            return new PractitionerInfo(p.getFirstName(), p.getLastName(), p.getCountryCode(), p.getLicenseNumber(),
                    p.getBio(), specialties, p.getVerificationStatus(), p.isTeleconsultationEnabled());
        }
    }

    public record StructureInfo(String name, OrganizationType type, String countryCode, String city, String address,
                                String email, String phone, String contactName, VerificationStatus verificationStatus) {
        static StructureInfo from(Organization o) {
            return new StructureInfo(o.getName(), o.getType(), o.getCountryCode(), o.getCity(), o.getAddress(),
                    o.getEmail(), o.getPhone(), o.getContactName(), o.getVerificationStatus());
        }
    }

    /**
     * Champs modifiables par le titulaire (null = inchangé). Patient : identité et adresse. Médecin : présentation
     * et téléconsultation (nom, numéro d'ordre et spécialités sont vérifiés par l'équipe : modification sur demande).
     */
    public record ProfileUpdate(
            @Size(max = 100) String firstName,
            @Size(max = 100) String lastName,
            LocalDate birthDate,
            Sex sex,
            @Pattern(regexp = "^$|^[A-Za-z]{2}$") String countryCode,
            @Size(max = 100) String city,
            @Size(max = 255) String address,
            @Size(max = 2000) String bio,
            Boolean teleconsultationEnabled,
            @Pattern(regexp = "^(fr|en)$") String locale,
            Boolean twoStepEnabled,
            Boolean notifyEmail,
            Boolean notifySms) {
    }

    /** Mes proches : personnes pour qui le titulaire prend rendez-vous. */
    public record RelativeInfo(UUID id, String firstName, String lastName, LocalDate birthDate, Sex sex,
                               Relative.Relation relation) {
        public static RelativeInfo from(Relative r) {
            return new RelativeInfo(r.getId(), r.getFirstName(), r.getLastName(), r.getBirthDate(), r.getSex(),
                    r.getRelation());
        }
    }

    public record RelativeInput(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            LocalDate birthDate,
            Sex sex,
            @jakarta.validation.constraints.NotNull Relative.Relation relation) {
    }

    public record PasswordChange(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 72, message = "Le mot de passe doit contenir de 8 à 72 caractères")
            String newPassword) {
    }
}
