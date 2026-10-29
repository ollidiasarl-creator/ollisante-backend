package com.ollisante.backend.service;

import com.ollisante.backend.common.ApiException;
import com.ollisante.backend.domain.Organization;
import com.ollisante.backend.domain.Patient;
import com.ollisante.backend.domain.Practitioner;
import com.ollisante.backend.domain.Relative;
import com.ollisante.backend.domain.User;
import com.ollisante.backend.dto.ProfileDtos.PasswordChange;
import com.ollisante.backend.dto.ProfileDtos.Profile;
import com.ollisante.backend.dto.ProfileDtos.ProfileUpdate;
import com.ollisante.backend.dto.ProfileDtos.RelativeInfo;
import com.ollisante.backend.dto.ProfileDtos.RelativeInput;
import com.ollisante.backend.repository.CountryRepository;
import com.ollisante.backend.repository.OrganizationRepository;
import com.ollisante.backend.repository.PatientRepository;
import com.ollisante.backend.repository.PractitionerRepository;
import com.ollisante.backend.repository.RefreshTokenRepository;
import com.ollisante.backend.repository.RelativeRepository;
import com.ollisante.backend.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Rubrique « Compte » de l'application et du site : consultation et modification par le titulaire. */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository users;
    private final PatientRepository patients;
    private final PractitionerRepository practitioners;
    private final OrganizationRepository organizations;
    private final CountryRepository countries;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final CountryScope countryScope;
    private final RelativeRepository relatives;

    private static final int MAX_RELATIVES = 10;

    @Transactional(readOnly = true)
    public Profile get(UUID userId) {
        User user = user(userId);
        return Profile.of(user, countryScope.of(userId, user.getRole()).orElse(null), patients.findByUserId(userId).orElse(null),
                practitioners.findByUserId(userId).orElse(null), organizations.findByUserId(userId).orElse(null));
    }

    @Transactional
    public Profile update(UUID userId, ProfileUpdate r) {
        User user = user(userId);
        if (r.locale() != null) user.setLocale(r.locale());
        if (r.twoStepEnabled() != null) {
            if (r.twoStepEnabled() && user.getEmail() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "EMAIL_REQUIS",
                        "Ajoutez une adresse e-mail à votre compte pour recevoir les codes de connexion.");
            }
            user.setTwoStepEnabled(r.twoStepEnabled());
        }
        if (r.notifyEmail() != null) user.setNotifyEmail(r.notifyEmail());
        if (r.notifySms() != null) user.setNotifySms(r.notifySms());
        patients.findByUserId(userId).ifPresent(p -> updatePatient(p, r));
        practitioners.findByUserId(userId).ifPresent(p -> {
            if (r.bio() != null) p.setBio(r.bio().isBlank() ? null : r.bio().trim());
            if (r.teleconsultationEnabled() != null) p.setTeleconsultationEnabled(r.teleconsultationEnabled());
        });
        return get(userId);
    }

    private void updatePatient(Patient p, ProfileUpdate r) {
        if (r.firstName() != null) p.setFirstName(required(r.firstName(), "Indiquez votre prénom."));
        if (r.lastName() != null) p.setLastName(required(r.lastName(), "Indiquez votre nom."));
        if (r.birthDate() != null) {
            if (r.birthDate().isAfter(LocalDate.now()) || r.birthDate().isBefore(LocalDate.of(1900, 1, 1))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_INVALIDE", "Date de naissance invalide.");
            }
            p.setBirthDate(r.birthDate());
        }
        if (r.sex() != null) p.setSex(r.sex());
        if (r.countryCode() != null) {
            String code = r.countryCode().isBlank() ? null : r.countryCode().toUpperCase(Locale.ROOT);
            if (code != null && !countries.existsById(code)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PAYS_INCONNU", "Pays inconnu.");
            }
            p.setCountryCode(code);
        }
        if (r.city() != null) p.setCity(r.city().isBlank() ? null : r.city().trim());
        if (r.address() != null) p.setAddress(r.address().isBlank() ? null : r.address().trim());
    }

    // ---------- mes proches ----------

    @Transactional(readOnly = true)
    public List<RelativeInfo> relatives(UUID userId) {
        return relatives.findByOwnerUserIdOrderByCreatedAtAsc(userId).stream().map(RelativeInfo::from).toList();
    }

    @Transactional
    public RelativeInfo addRelative(UUID userId, RelativeInput r) {
        if (relatives.findByOwnerUserIdOrderByCreatedAtAsc(userId).size() >= MAX_RELATIVES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TROP_DE_PROCHES", "Vous pouvez ajouter jusqu'à " + MAX_RELATIVES + " proches.");
        }
        Relative rel = new Relative();
        rel.setOwnerUserId(userId);
        fill(rel, r);
        return RelativeInfo.from(relatives.save(rel));
    }

    @Transactional
    public RelativeInfo updateRelative(UUID userId, UUID id, RelativeInput r) {
        Relative rel = relatives.findByIdAndOwnerUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROCHE_INTROUVABLE", "Proche introuvable."));
        fill(rel, r);
        return RelativeInfo.from(rel);
    }

    @Transactional
    public void deleteRelative(UUID userId, UUID id) {
        relatives.findByIdAndOwnerUserId(id, userId).ifPresent(relatives::delete);
    }

    private static void fill(Relative rel, RelativeInput r) {
        if (r.birthDate() != null && (r.birthDate().isAfter(LocalDate.now()) || r.birthDate().isBefore(LocalDate.of(1900, 1, 1)))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_INVALIDE", "Date de naissance invalide.");
        }
        rel.setFirstName(r.firstName().trim());
        rel.setLastName(r.lastName().trim());
        rel.setBirthDate(r.birthDate());
        rel.setSex(r.sex());
        rel.setRelation(r.relation());
    }

    /** Changement du mot de passe depuis le compte : l'ancien est demandé, les autres appareils sont déconnectés. */
    @Transactional
    public void changePassword(UUID userId, PasswordChange r) {
        User user = user(userId);
        if (user.getPasswordHash() == null || !passwordEncoder.matches(r.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MOT_DE_PASSE_ACTUEL_INCORRECT",
                    "Le mot de passe actuel est incorrect.");
        }
        user.setPasswordHash(passwordEncoder.encode(r.newPassword()));
        users.saveAndFlush(user);
        refreshTokens.revokeAllForUser(userId);
    }

    private User user(UUID id) {
        return users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "NON_AUTHENTIFIE",
                "Authentification requise."));
    }

    private static String required(String value, String message) {
        if (value.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "CHAMP_OBLIGATOIRE", message);
        return value.trim();
    }
}
