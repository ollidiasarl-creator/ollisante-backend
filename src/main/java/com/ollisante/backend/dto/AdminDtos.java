package com.ollisante.backend.dto;

import com.ollisante.backend.domain.AdminAuditEntry;
import com.ollisante.backend.domain.Country;
import com.ollisante.backend.domain.Role;
import com.ollisante.backend.domain.UserStatus;
import com.ollisante.backend.domain.AppointmentMode;
import com.ollisante.backend.domain.AppointmentStatus;
import com.ollisante.backend.domain.OrganizationType;
import com.ollisante.backend.domain.PaymentMode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Échanges du site d'administration. */
public final class AdminDtos {

    private AdminDtos() {
    }

    public record CountryAdmin(String code, String nameFr, String nameEn, String currency, String dialCode,
                               String timezone, Long defaultFeeMinor, boolean active, boolean onlinePayment,
                               boolean onSitePayment, String emergencyNumber) {
        public static CountryAdmin from(Country c) {
            return new CountryAdmin(c.getCode(), c.getNameFr(), c.getNameEn(), c.getCurrency(), c.getDialCode(),
                    c.getTimezone(), c.getDefaultFeeMinor(), c.isActive(), c.isOnlinePayment(), c.isOnSitePayment(),
                    c.getEmergencyNumber());
        }
    }

    public record CountryCreate(
            @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$", message = "Code pays sur 2 lettres (ISO), ex. SN") String code,
            @NotBlank @Size(max = 100) String nameFr,
            @NotBlank @Size(max = 100) String nameEn,
            @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$", message = "Devise sur 3 lettres, ex. XOF") String currency,
            @NotBlank @Pattern(regexp = "^\\+\\d{1,4}$", message = "Indicatif, ex. +221") String dialCode,
            @NotBlank @Size(max = 40) String timezone,
            @Min(0) Long defaultFeeMinor) {
    }

    public record CountryUpdate(@Size(max = 100) String nameFr, @Size(max = 100) String nameEn,
                                @Size(max = 40) String timezone, @Min(0) Long defaultFeeMinor,
                                Boolean clearDefaultFee, Boolean active, Boolean onlinePayment, Boolean onSitePayment,
                                @Pattern(regexp = "^$|^\\+?[0-9 ]{2,20}$", message = "Numéro d'urgence : chiffres uniquement, ex. 1515")
                                String emergencyNumber) {
    }

    /** Réglages de l'application modifiables par l'administrateur (vide = bouton masqué). */
    public record Settings(
            @Pattern(regexp = "^$|^\\+[1-9]\\d{7,14}$", message = "Numéro WhatsApp au format international, ex. +224620000000")
            String whatsappNumber,
            @Size(max = 200) String whatsappHours) {
    }

    public record SpecialtySave(
            @Pattern(regexp = "^[A-Z][A-Z_]{1,49}$", message = "Code en majuscules, ex. GASTROENTEROLOGY") String code,
            @NotBlank @Size(max = 100) String nameFr,
            @NotBlank @Size(max = 100) String nameEn) {
    }

    public record UserAdmin(UUID id, String email, String phone, Role role, UserStatus status, String firstName,
                            String lastName, String countryCode, Instant createdAt) {
    }

    public record StatusUpdate(@NotNull UserStatus status) {
    }

    public record Stats(long patients, long practitionersVerified, long practitionersPending, long facilities,
                        long news, long appointmentsToday, long appointmentsThisMonth, long appointmentsUpcoming,
                        long cancelledThisMonth) {
    }

    public record AuditEntry(UUID id, String adminEmail, String action, String targetType, String targetId,
                             String details, Instant createdAt) {
        public static AuditEntry from(AdminAuditEntry e) {
            return new AuditEntry(e.getId(), e.getAdminEmail(), e.getAction(), e.getTargetType(), e.getTargetId(),
                    e.getDetails(), e.getCreatedAt());
        }
    }

    /** Patient ajouté par l'administrateur : il choisit son mot de passe avec « Mot de passe oublié ? ». */
    public record PatientCreate(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Email @Size(max = 255) String email,
            @Pattern(regexp = "^$|^\\+[1-9]\\d{7,14}$", message = "Numéro au format international, ex. +221770000000")
            String phone,
            @Size(min = 2, max = 2) String countryCode,
            @Pattern(regexp = "^$|fr|en", message = "Langue acceptée : fr ou en") String locale,
            /** Comptes de test uniquement : mot de passe fixé par l'administrateur, pas d'e-mail de bienvenue. */
            @Size(min = 8, max = 72, message = "Le mot de passe doit contenir de 8 à 72 caractères") String password) {
    }

    public record PatientUpdate(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Numéro au format international, ex. +221770000000")
            String phone,
            @Size(min = 2, max = 2) String countryCode) {
    }

    public record PatientCreated(UserAdmin patient, boolean welcomeEmailSent) {
    }

    /** Consultation d'un patient vue par l'administrateur (sans le motif, donnée médicale). */
    public record PatientAppointment(UUID id, Instant startsAt, AppointmentStatus status, AppointmentMode mode,
                                     String practitionerName, List<String> specialties, String facilityName,
                                     String organizationName, PaymentMode paymentMode, Long priceMinor,
                                     String priceCurrency, String paymentStatus, String operator,
                                     long refundedMinor) {
    }

    /** Modification d'une structure par l'administrateur (l'e-mail de connexion ne change pas ici). */
    public record StructureUpdate(
            @NotNull OrganizationType type,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 100) String city,
            @Size(max = 255) String address,
            @NotBlank @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Numéro au format international, ex. +221330000000")
            String phone,
            @Size(max = 255) @Pattern(regexp = "^$|^[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", message = "Nom de domaine invalide")
            String domain,
            @NotBlank @Size(max = 100) String registrationNumber,
            @NotBlank @Size(max = 200) String contactName,
            @Size(max = 100) String contactRole,
            @Min(0) @Max(100000) Integer beds) {
    }
}
