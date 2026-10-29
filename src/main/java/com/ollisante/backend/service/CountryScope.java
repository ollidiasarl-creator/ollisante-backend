package com.ollisante.backend.service;

import com.ollisante.backend.domain.Country;
import com.ollisante.backend.domain.Role;
import com.ollisante.backend.domain.User;
import com.ollisante.backend.repository.CountryRepository;
import com.ollisante.backend.repository.OrganizationRepository;
import com.ollisante.backend.repository.PatientRepository;
import com.ollisante.backend.repository.PractitionerRepository;
import com.ollisante.backend.repository.UserRepository;
import com.ollisante.backend.security.CurrentUser;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pays du compte connecté : chacun ne voit que son pays (professionnels, établissements, disponibilités,
 * tarifs, actualités) et ne réserve que dans son pays. Patient : le pays de son profil, à défaut celui de son
 * numéro de téléphone (connexion par SMS) ; médecin : son pays d'exercice ; structure : son pays.
 * Administrateur et visiteur non connecté : pas de filtre.
 */
@Component
@RequiredArgsConstructor
public class CountryScope {

    private final UserRepository users;
    private final PatientRepository patients;
    private final PractitionerRepository practitioners;
    private final OrganizationRepository organizations;
    private final CountryRepository countries;

    @Transactional(readOnly = true)
    public Optional<String> of(Authentication authentication) {
        Role role = CurrentUser.roleOrNull(authentication);
        if (role == null || role == Role.ADMIN) return Optional.empty();
        return of(CurrentUser.id(authentication), role);
    }

    @Transactional(readOnly = true)
    public Optional<String> of(UUID userId, Role role) {
        return switch (role) {
            case PATIENT -> patients.findByUserId(userId)
                    .map(p -> p.getCountryCode())
                    .filter(c -> c != null && !c.isBlank())
                    .or(() -> users.findById(userId).map(User::getPhone).flatMap(this::countryOfPhone));
            case PRACTITIONER -> practitioners.findByUserId(userId).map(p -> p.getCountryCode());
            case STRUCTURE -> organizations.findByUserId(userId).map(o -> o.getCountryCode());
            default -> Optional.empty();
        };
    }

    /** +221… → SN : l'indicatif le plus long qui correspond (+1 et +1xxx ne se confondent pas). */
    private Optional<String> countryOfPhone(String phone) {
        if (phone == null || !phone.startsWith("+")) return Optional.empty();
        return countries.findAll().stream()
                .filter(c -> c.getDialCode() != null && phone.startsWith(c.getDialCode()))
                .max(Comparator.comparingInt(c -> c.getDialCode().length()))
                .map(Country::getCode);
    }
}
