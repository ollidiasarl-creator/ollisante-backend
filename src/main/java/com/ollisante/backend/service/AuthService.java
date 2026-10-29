package com.ollisante.backend.service;

import com.ollisante.backend.common.ApiException;
import com.ollisante.backend.config.JwtProperties;
import com.ollisante.backend.domain.Country;
import com.ollisante.backend.domain.EmailSignup;
import com.ollisante.backend.domain.Facility;
import com.ollisante.backend.domain.Organization;
import com.ollisante.backend.domain.Patient;
import com.ollisante.backend.domain.Practitioner;
import com.ollisante.backend.domain.RefreshToken;
import com.ollisante.backend.domain.Role;
import com.ollisante.backend.domain.Specialty;
import com.ollisante.backend.domain.User;
import com.ollisante.backend.domain.UserStatus;
import com.ollisante.backend.domain.VerificationStatus;
import com.ollisante.backend.dto.AdminPractitionerCreate;
import com.ollisante.backend.dto.AuthResponse;
import com.ollisante.backend.dto.LoginRequest;
import com.ollisante.backend.dto.RegisterPatientRequest;
import com.ollisante.backend.dto.RegisterPractitionerRequest;
import com.ollisante.backend.dto.StructureDtos;
import com.ollisante.backend.dto.UserResponse;
import com.ollisante.backend.repository.CountryRepository;
import com.ollisante.backend.repository.FacilityRepository;
import com.ollisante.backend.repository.OrganizationRepository;
import com.ollisante.backend.repository.PatientRepository;
import com.ollisante.backend.repository.PractitionerRepository;
import com.ollisante.backend.repository.RefreshTokenRepository;
import com.ollisante.backend.repository.SpecialtyRepository;
import com.ollisante.backend.repository.UserRepository;
import com.ollisante.backend.security.JwtService;
import com.ollisante.backend.security.LoginAttempts;
import com.ollisante.backend.security.TokenHasher;
import jakarta.annotation.PostConstruct;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final PatientRepository patients;
    private final PractitionerRepository practitioners;
    private final CountryRepository countries;
    private final SpecialtyRepository specialties;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final OrganizationRepository organizations;
    private final FacilityRepository facilities;
    private final LoginAttempts loginAttempts;

    /** Empreinte factice : on compare toujours un mot de passe, même si le compte n'existe pas (timing). */
    private String dummyHash;

    @PostConstruct
    void init() {
        dummyHash = passwordEncoder.encode("ollisante-dummy-password");
    }

    /**
     * Vérifie une demande d'inscription patient par e-mail avant l'envoi du code (pays connu, e-mail libre).
     * Le téléphone d'un patient se vérifie par SMS : il n'est pas accepté ici.
     */
    public void checkPatientSignup(RegisterPatientRequest r) {
        if (r.email() == null || r.email().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDENTIFIANT_REQUIS", "L'adresse e-mail est requise.");
        }
        if (r.phone() != null && !r.phone().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TELEPHONE_VIA_SMS",
                    "Pour utiliser un numéro de téléphone, connectez-vous avec le code SMS.");
        }
        if (r.countryCode() != null) requireCountry(r.countryCode().toUpperCase(Locale.ROOT));
        if (users.existsByEmail(r.email().trim().toLowerCase(Locale.ROOT))) {
            throw new ApiException(HttpStatus.CONFLICT, "COMPTE_EXISTANT", "Un compte existe déjà avec cet identifiant.");
        }
    }

    public String encodePassword(String raw) {
        return passwordEncoder.encode(raw);
    }

    /** Crée le compte patient une fois l'adresse e-mail confirmée par le code reçu. */
    @Transactional
    public AuthResponse completeEmailSignup(EmailSignup signup) {
        User user = newUser(signup.getEmail(), null, signup.getPasswordHash(), Role.PATIENT, signup.getLocale());
        Patient patient = new Patient();
        patient.setUser(user);
        patient.setFirstName(signup.getFirstName());
        patient.setLastName(signup.getLastName());
        patient.setBirthDate(signup.getBirthDate());
        patient.setCountryCode(signup.getCountryCode());
        patients.save(patient);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse registerPractitioner(RegisterPractitionerRequest r) {
        Practitioner practitioner = createPractitioner(r.email(), r.phone(), r.password(), r.firstName(),
                r.lastName(), r.countryCode(), r.licenseNumber(), r.specialtyCodes(), r.locale());
        return issueTokens(practitioner.getUser());
    }

    /**
     * Professionnel ajouté par l'administrateur : validé d'office, avec un mot de passe aléatoire que personne
     * ne connaît (le médecin choisit le sien avec « Mot de passe oublié ? »), ou celui fourni pour un compte de test.
     */
    @Transactional
    public Practitioner createPractitionerByAdmin(AdminPractitionerCreate r) {
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String password = r.password() != null ? r.password()
                : Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        String locale = r.locale() == null || r.locale().isBlank() ? null : r.locale();
        Practitioner practitioner = createPractitioner(r.email(), r.phone(), password, r.firstName(), r.lastName(),
                r.countryCode(), r.licenseNumber(), r.specialtyCodes(), locale);
        practitioner.setVerificationStatus(VerificationStatus.VERIFIED);
        return practitioner;
    }

    /**
     * Patient ajouté par l'administrateur : mot de passe aléatoire (il choisit le sien par « Mot de passe oublié ? »),
     * ou celui fourni pour un compte de test.
     */
    @Transactional
    public Patient createPatientByAdmin(String email, String phone, String firstName, String lastName,
                                        String rawCountryCode, String locale, String password) {
        String countryCode = rawCountryCode == null || rawCountryCode.isBlank() ? null
                : rawCountryCode.toUpperCase(Locale.ROOT);
        if (countryCode != null) requireCountry(countryCode);
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String pwd = password != null ? password : Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        User user = createUser(email, phone, pwd, Role.PATIENT, locale == null || locale.isBlank() ? null : locale);
        Patient patient = new Patient();
        patient.setUser(user);
        patient.setFirstName(firstName.trim());
        patient.setLastName(lastName.trim());
        patient.setCountryCode(countryCode);
        return patients.save(patient);
    }

    private Practitioner createPractitioner(String email, String phone, String password, String firstName,
                                            String lastName, String rawCountryCode, String licenseNumber,
                                            List<String> specialtyCodes, String locale) {
        String countryCode = rawCountryCode.toUpperCase(Locale.ROOT);
        Country country = requireCountry(countryCode);

        Set<String> codes = new HashSet<>(specialtyCodes);
        List<Specialty> found = specialties.findByCodeIn(codes);
        if (found.size() != codes.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SPECIALITE_INCONNUE",
                    "Au moins une spécialité est inconnue.");
        }

        User user = createUser(email, phone, password, Role.PRACTITIONER, locale);

        Practitioner practitioner = new Practitioner();
        practitioner.setUser(user);
        practitioner.setFirstName(firstName.trim());
        practitioner.setLastName(lastName.trim());
        practitioner.setCountryCode(countryCode);
        practitioner.setLicenseNumber(licenseNumber.trim());
        practitioner.setSpecialties(new HashSet<>(found));
        practitioner.setFeeCurrency(country.getCurrency());
        practitioner.setConsultationFeeMinor(country.getDefaultFeeMinor());
        return practitioners.save(practitioner);
    }

    /**
     * Inscription d'une structure : compte de connexion, fiche (en attente de vérification par OLIDIA)
     * et premier site, aux coordonnées de la structure.
     */
    @Transactional
    public AuthResponse registerStructure(StructureDtos.RegisterStructureRequest r) {
        return issueTokens(createStructure(r).getUser());
    }

    /**
     * Structure ajoutée par l'administrateur : validée d'office, mot de passe aléatoire (elle choisit le sien par
     * « Mot de passe oublié ? »).
     */
    @Transactional
    public Organization createStructureByAdmin(StructureDtos.AdminStructureCreate r) {
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        Organization o = createStructure(r.withPassword(Base64.getUrlEncoder().withoutPadding().encodeToString(secret)));
        o.setVerificationStatus(VerificationStatus.VERIFIED);
        return o;
    }

    private Organization createStructure(StructureDtos.RegisterStructureRequest r) {
        String countryCode = r.countryCode().toUpperCase(Locale.ROOT);
        requireCountry(countryCode);
        // Le numéro de la structure sert aussi d'identifiant de connexion, s'il n'est pas déjà pris.
        String phone = r.phone().trim();
        User user = createUser(r.email(), users.existsByPhone(phone) ? null : phone, r.password(),
                Role.STRUCTURE, r.locale());

        Organization o = new Organization();
        o.setUser(user);
        o.setType(r.type());
        o.setName(r.name().trim());
        o.setCountryCode(countryCode);
        o.setCity(blankToNull(r.city()));
        o.setAddress(blankToNull(r.address()));
        o.setEmail(user.getEmail());
        o.setPhone(phone);
        o.setDomain(r.domain() == null || r.domain().isBlank() ? null : r.domain().trim().toLowerCase(Locale.ROOT));
        o.setRegistrationNumber(r.registrationNumber().trim());
        o.setContactName(r.contactName().trim());
        o.setContactRole(blankToNull(r.contactRole()));
        o.setBeds(r.beds());
        organizations.save(o);

        Facility site = new Facility();
        site.setName(o.getName());
        site.setCountryCode(countryCode);
        site.setCity(o.getCity());
        site.setAddress(o.getAddress());
        site.setPhone(o.getPhone());
        site.setOrganizationId(o.getId());
        facilities.save(site);
        return o;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /**
     * Appelée après vérification du code SMS. Connecte le compte existant, ou crée un compte patient
     * (prénom et nom requis) sans mot de passe. noRollbackFor : NOM_REQUIS ne doit pas annuler la transaction appelante.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse completePhoneLogin(String phone, String firstName, String lastName, String locale) {
        Optional<User> found = users.findByPhone(phone);
        if (found.isPresent()) {
            User existing = found.get();
            requireActive(existing);
            return issueTokens(existing);
        }
        if (firstName == null || firstName.isBlank() || lastName == null || lastName.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOM_REQUIS",
                    "Prénom et nom requis pour créer votre compte.");
        }
        User user = new User();
        user.setPhone(phone);
        user.setRole(Role.PATIENT);
        user.setLocale(locale == null ? "fr" : locale);
        users.save(user);

        Patient patient = new Patient();
        patient.setUser(user);
        patient.setFirstName(firstName.trim());
        patient.setLastName(lastName.trim());
        patients.save(patient);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest r) {
        return issueTokens(authenticate(r));
    }

    /** Vérifie identifiant et mot de passe (avec blocage après trop d'échecs) sans ouvrir de session. */
    @Transactional(readOnly = true)
    public User authenticate(LoginRequest r) {
        String identifier = r.identifier().trim().replace(" ", "");
        String key = identifier.toLowerCase(Locale.ROOT);
        if (loginAttempts.isLocked(key)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "CONNEXION_BLOQUEE",
                    "Trop d'essais de mot de passe. Réessayez dans 15 minutes ou utilisez « Mot de passe oublié ? ».");
        }
        Optional<User> found = identifier.contains("@")
                ? users.findByEmail(key)
                : users.findByPhone(identifier);

        String hash = found.map(User::getPasswordHash).orElse(dummyHash);
        boolean passwordOk = passwordEncoder.matches(r.password(), hash);
        if (found.isEmpty() || !passwordOk) {
            loginAttempts.failed(key);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "IDENTIFIANTS_INVALIDES",
                    "Identifiant ou mot de passe incorrect.");
        }
        loginAttempts.succeeded(key);
        User user = found.get();
        requireActive(user);
        return user;
    }

    /** Ouvre une session pour un utilisateur déjà vérifié (mot de passe, et code e-mail pour un administrateur). */
    @Transactional
    public AuthResponse tokensFor(User user) {
        return issueTokens(user);
    }

    /**
     * Rotation du refresh token : l'ancien est révoqué à chaque usage. Si un jeton déjà révoqué est
     * présenté (vol possible), toutes les sessions de l'utilisateur sont révoquées.
     * noRollbackFor : la révocation doit être conservée même si on lève ensuite une exception.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(String rawRefreshToken) {
        RefreshToken token = refreshTokens.findByTokenHash(TokenHasher.sha256Hex(rawRefreshToken))
                .orElseThrow(AuthService::invalidRefreshToken);

        if (token.isRevoked()) {
            refreshTokens.revokeAllForUser(token.getUser().getId());
            throw invalidRefreshToken();
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw invalidRefreshToken();
        }
        User user = token.getUser();
        requireActive(user);

        token.setRevoked(true);
        return issueTokens(user);
    }

    /** Fin du « mot de passe oublié » : nouveau mot de passe, et toutes les anciennes sessions sont fermées. */
    @Transactional
    public AuthResponse completePasswordReset(UUID userId, String newPassword) {
        refreshTokens.revokeAllForUser(userId); // vide le contexte JPA : l'utilisateur est relu ensuite
        User user = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "CODE_INVALIDE", "Code incorrect ou expiré."));
        requireActive(user);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        // Nouveau mot de passe choisi : on lève un éventuel blocage pour trop d'essais.
        if (user.getEmail() != null) loginAttempts.succeeded(user.getEmail());
        if (user.getPhone() != null) loginAttempts.succeeded(user.getPhone().toLowerCase(Locale.ROOT));
        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokens.findByTokenHash(TokenHasher.sha256Hex(rawRefreshToken))
                .ifPresent(t -> t.setRevoked(true));
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "NON_AUTHENTIFIE",
                        "Authentification requise."));
        return UserResponse.from(user);
    }

    // ---------- helpers ----------

    private User createUser(String rawEmail, String rawPhone, String password, Role role, String locale) {
        return newUser(rawEmail, rawPhone, passwordEncoder.encode(password), role, locale);
    }

    private User newUser(String rawEmail, String rawPhone, String passwordHash, Role role, String locale) {
        String email = (rawEmail == null || rawEmail.isBlank()) ? null : rawEmail.trim().toLowerCase(Locale.ROOT);
        String phone = (rawPhone == null || rawPhone.isBlank()) ? null : rawPhone.trim();
        if (email == null && phone == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDENTIFIANT_REQUIS",
                    "Un e-mail ou un numéro de téléphone est requis.");
        }
        if ((email != null && users.existsByEmail(email)) || (phone != null && users.existsByPhone(phone))) {
            throw new ApiException(HttpStatus.CONFLICT, "COMPTE_EXISTANT",
                    "Un compte existe déjà avec cet identifiant.");
        }
        User user = new User();
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(passwordHash);
        user.setRole(role);
        user.setLocale(locale == null ? "fr" : locale);
        return users.save(user);
    }

    private Country requireCountry(String code) {
        return countries.findById(code)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "PAYS_INCONNU", "Pays inconnu."));
    }

    private static void requireActive(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "COMPTE_SUSPENDU", "Ce compte est suspendu.");
        }
    }

    private static ApiException invalidRefreshToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALIDE",
                "Session expirée, veuillez vous reconnecter.");
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getRole());

        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String rawRefresh = Base64.getUrlEncoder().withoutPadding().encodeToString(random);

        RefreshToken refresh = new RefreshToken();
        refresh.setUser(user);
        refresh.setTokenHash(TokenHasher.sha256Hex(rawRefresh));
        refresh.setExpiresAt(Instant.now().plus(jwtProperties.refreshTtlDays(), ChronoUnit.DAYS));
        refreshTokens.save(refresh);

        return new AuthResponse(accessToken, rawRefresh, "Bearer", jwtService.accessTtlSeconds(),
                UserResponse.from(user));
    }
}
