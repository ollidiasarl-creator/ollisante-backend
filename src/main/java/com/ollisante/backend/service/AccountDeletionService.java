package com.ollisante.backend.service;

import com.ollisante.backend.common.ApiException;
import com.ollisante.backend.domain.Appointment;
import com.ollisante.backend.domain.AppointmentStatus;
import com.ollisante.backend.domain.Patient;
import com.ollisante.backend.domain.Practitioner;
import com.ollisante.backend.domain.Role;
import com.ollisante.backend.domain.User;
import com.ollisante.backend.domain.UserStatus;
import com.ollisante.backend.domain.VerificationStatus;
import com.ollisante.backend.repository.AppointmentRepository;
import com.ollisante.backend.repository.AvailabilityRuleRepository;
import com.ollisante.backend.repository.OtpCodeRepository;
import com.ollisante.backend.repository.PasswordResetCodeRepository;
import com.ollisante.backend.repository.PatientRepository;
import com.ollisante.backend.repository.PractitionerRepository;
import com.ollisante.backend.repository.RefreshTokenRepository;
import com.ollisante.backend.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Suppression du compte demandée depuis l'application (exigée par Apple et Google).
 * Le compte est anonymisé plutôt qu'effacé : les rendez-vous passés restent dans l'historique du praticien,
 * mais plus rien ne permet d'identifier la personne. Les rendez-vous à venir sont annulés.
 */
@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<AppointmentStatus> ACTIVE =
            Set.of(AppointmentStatus.CONFIRMED, AppointmentStatus.PENDING_PAYMENT);

    private final UserRepository users;
    private final PatientRepository patients;
    private final PractitionerRepository practitioners;
    private final AppointmentRepository appointments;
    private final AvailabilityRuleRepository availabilityRules;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetCodeRepository resetCodes;
    private final OtpCodeRepository otpCodes;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void delete(UUID userId) {
        refreshTokens.revokeAllForUser(userId); // vide le contexte JPA : tout est relu ensuite
        User user = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "NON_AUTHENTIFIE",
                        "Authentification requise."));
        if (user.getRole() == Role.STRUCTURE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "STRUCTURE_SUPPRESSION_SUPPORT",
                    "La fermeture du compte d'une structure se fait auprès de l'équipe OLIDIA.");
        }
        if (user.getRole() == Role.ADMIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ADMIN_NON_SUPPRIMABLE",
                    "Un compte administrateur ne peut pas être supprimé depuis l'application.");
        }
        Instant now = Instant.now();

        patients.findByUserId(userId).ifPresent(p -> anonymizePatient(p, now));
        practitioners.findByUserId(userId).ifPresent(p -> anonymizePractitioner(p, now));

        resetCodes.deleteByUserId(userId);
        if (user.getPhone() != null) otpCodes.deleteByPhone(user.getPhone());

        byte[] random = new byte[24];
        RANDOM.nextBytes(random);
        user.setEmail("supprime-" + user.getId() + "@ollisante.invalid");
        user.setPhone(null);
        user.setPasswordHash(passwordEncoder.encode(Base64.getEncoder().encodeToString(random)));
        user.setStatus(UserStatus.DELETED);
    }

    private void anonymizePatient(Patient p, Instant now) {
        for (Appointment a : appointments.findByPatientIdOrderByStartsAtDesc(p.getId())) {
            cancelIfUpcoming(a, now);
            a.setReason(null); // motif de consultation : donnée de santé
        }
        p.setFirstName("Patient");
        p.setLastName("supprimé");
        p.setBirthDate(null);
        p.setSex(null);
        p.setCity(null);
        p.setAddress(null);
        p.setHeightCm(null);
        p.setWeightKg(null);
    }

    private void anonymizePractitioner(Practitioner p, Instant now) {
        List<Appointment> list = appointments.findByPractitionerIdOrderByStartsAtDesc(p.getId());
        list.forEach(a -> cancelIfUpcoming(a, now));
        availabilityRules.deleteAll(availabilityRules.findByPractitionerId(p.getId()));
        p.setVerificationStatus(VerificationStatus.REJECTED); // n'apparaît plus dans les recherches
        p.setFirstName("Praticien");
        p.setLastName("supprimé");
        p.setLicenseNumber("SUPPRIME");
        p.setBio(null);
    }

    private static void cancelIfUpcoming(Appointment a, Instant now) {
        if (ACTIVE.contains(a.getStatus()) && a.getStartsAt().isAfter(now)) {
            a.setStatus(AppointmentStatus.CANCELLED);
            a.setCancelledReason("COMPTE_SUPPRIME");
        }
    }
}
