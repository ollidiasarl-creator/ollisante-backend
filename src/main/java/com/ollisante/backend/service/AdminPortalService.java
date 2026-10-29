package com.ollisante.backend.service;

import com.ollisante.backend.common.ApiException;
import com.ollisante.backend.domain.Appointment;
import com.ollisante.backend.domain.AppointmentStatus;
import com.ollisante.backend.domain.Country;
import com.ollisante.backend.domain.Facility;
import com.ollisante.backend.domain.Organization;
import com.ollisante.backend.domain.Patient;
import com.ollisante.backend.domain.Payment;
import com.ollisante.backend.domain.Role;
import com.ollisante.backend.domain.Specialty;
import com.ollisante.backend.domain.User;
import com.ollisante.backend.domain.UserStatus;
import com.ollisante.backend.domain.VerificationStatus;
import com.ollisante.backend.dto.AdminDtos.AuditEntry;
import com.ollisante.backend.dto.AdminDtos.CountryAdmin;
import com.ollisante.backend.dto.AdminDtos.CountryCreate;
import com.ollisante.backend.dto.AdminDtos.CountryUpdate;
import com.ollisante.backend.dto.AdminDtos.PatientAppointment;
import com.ollisante.backend.dto.AdminDtos.PatientCreate;
import com.ollisante.backend.dto.AdminDtos.PatientCreated;
import com.ollisante.backend.dto.AdminDtos.PatientUpdate;
import com.ollisante.backend.dto.AdminDtos.SpecialtySave;
import com.ollisante.backend.dto.AdminDtos.Stats;
import com.ollisante.backend.dto.AdminDtos.StructureUpdate;
import com.ollisante.backend.dto.AdminDtos.UserAdmin;
import com.ollisante.backend.dto.FacilityRequest;
import com.ollisante.backend.dto.FacilityResponse;
import com.ollisante.backend.dto.PageResponse;
import com.ollisante.backend.dto.SpecialtyResponse;
import com.ollisante.backend.dto.StructureDtos;
import com.ollisante.backend.dto.StructureDtos.StructureProfile;
import com.ollisante.backend.mail.WelcomeMail;
import com.ollisante.backend.repository.AdminAuditRepository;
import com.ollisante.backend.repository.AppointmentRepository;
import com.ollisante.backend.repository.AvailabilityRuleRepository;
import com.ollisante.backend.repository.CountryRepository;
import com.ollisante.backend.repository.FacilityRepository;
import com.ollisante.backend.repository.NewsRepository;
import com.ollisante.backend.repository.OrganizationRepository;
import com.ollisante.backend.repository.PatientRepository;
import com.ollisante.backend.repository.PaymentRepository;
import com.ollisante.backend.repository.PractitionerRepository;
import com.ollisante.backend.repository.RefreshTokenRepository;
import com.ollisante.backend.repository.SpecialtyRepository;
import com.ollisante.backend.repository.UserRepository;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Site d'administration : tableau de bord, données de référence, utilisateurs, journal. */
@Service
@RequiredArgsConstructor
public class AdminPortalService {

    private static final Set<AppointmentStatus> ACTIVE =
            Set.of(AppointmentStatus.CONFIRMED, AppointmentStatus.PENDING_PAYMENT);

    private final CountryRepository countries;
    private final SpecialtyRepository specialties;
    private final FacilityRepository facilities;
    private final AvailabilityRuleRepository availabilityRules;
    private final AppointmentRepository appointments;
    private final PatientRepository patients;
    private final PractitionerRepository practitioners;
    private final UserRepository users;
    private final NewsRepository news;
    private final RefreshTokenRepository refreshTokens;
    private final AdminAuditRepository auditLog;
    private final AuditService audit;
    private final OrganizationRepository organizationRepo;
    private final PaymentRepository payments;
    private final AuthService authService;
    private final WelcomeMail welcomeMail;

    // ---------- tableau de bord ----------

    @Transactional(readOnly = true)
    public Stats stats() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant dayStart = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant monthStart = today.withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant monthEnd = today.withDayOfMonth(1).plusMonths(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new Stats(
                users.countByRoleAndStatus(Role.PATIENT, UserStatus.ACTIVE),
                practitioners.countByVerificationStatusAndUserStatusNot(VerificationStatus.VERIFIED, UserStatus.DELETED),
                practitioners.countByVerificationStatusAndUserStatusNot(VerificationStatus.PENDING, UserStatus.DELETED),
                facilities.count(),
                news.count(),
                appointments.countByStartsAtGreaterThanEqualAndStartsAtLessThan(dayStart, dayStart.plusSeconds(86_400)),
                appointments.countByStartsAtGreaterThanEqualAndStartsAtLessThan(monthStart, monthEnd),
                appointments.countByStatusInAndStartsAtAfter(ACTIVE, Instant.now()),
                appointments.countByStatusAndStartsAtGreaterThanEqualAndStartsAtLessThan(
                        AppointmentStatus.CANCELLED, monthStart, monthEnd));
    }

    // ---------- pays ----------

    @Transactional(readOnly = true)
    public List<CountryAdmin> countries() {
        return countries.findAll(Sort.by("nameFr")).stream().map(CountryAdmin::from).toList();
    }

    @Transactional
    public CountryAdmin createCountry(UUID admin, CountryCreate r) {
        String code = r.code().toUpperCase(Locale.ROOT);
        if (countries.existsById(code)) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYS_EXISTANT", "Ce pays existe déjà.");
        }
        Country c = new Country();
        c.setCode(code);
        c.setNameFr(r.nameFr().trim());
        c.setNameEn(r.nameEn().trim());
        c.setCurrency(r.currency().toUpperCase(Locale.ROOT));
        c.setDialCode(r.dialCode());
        c.setTimezone(requireZone(r.timezone()));
        c.setDefaultFeeMinor(r.defaultFeeMinor());
        c.setActive(true);
        countries.save(c);
        audit.record(admin, "PAYS_AJOUTE", "COUNTRY", code, c.getNameFr());
        return CountryAdmin.from(c);
    }

    @Transactional
    public CountryAdmin updateCountry(UUID admin, String code, CountryUpdate r) {
        Country c = countries.findById(code.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYS_INCONNU", "Pays inconnu."));
        if (r.nameFr() != null && !r.nameFr().isBlank()) c.setNameFr(r.nameFr().trim());
        if (r.nameEn() != null && !r.nameEn().isBlank()) c.setNameEn(r.nameEn().trim());
        if (r.timezone() != null && !r.timezone().isBlank()) c.setTimezone(requireZone(r.timezone()));
        if (Boolean.TRUE.equals(r.clearDefaultFee())) c.setDefaultFeeMinor(null);
        else if (r.defaultFeeMinor() != null) c.setDefaultFeeMinor(r.defaultFeeMinor());
        if (r.active() != null) c.setActive(r.active());
        if (r.onlinePayment() != null) c.setOnlinePayment(r.onlinePayment());
        if (r.onSitePayment() != null) c.setOnSitePayment(r.onSitePayment());
        if (r.emergencyNumber() != null) c.setEmergencyNumber(r.emergencyNumber().isBlank() ? null : r.emergencyNumber().trim());
        audit.record(admin, "PAYS_MODIFIE", "COUNTRY", c.getCode(),
                c.getNameFr() + (c.isActive() ? " (actif" : " (désactivé") + ", paiement : "
                        + (c.isOnlinePayment() ? "Mobile Money" : "") + (c.isOnlinePayment() && c.isOnSitePayment() ? " + " : "")
                        + (c.isOnSitePayment() ? "sur place" : "") + (!c.isOnlinePayment() && !c.isOnSitePayment() ? "aucun" : "") + ")");
        return CountryAdmin.from(c);
    }

    private static String requireZone(String zone) {
        try {
            return ZoneId.of(zone.trim()).getId();
        } catch (DateTimeException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FUSEAU_INVALIDE",
                    "Fuseau horaire invalide (ex. Africa/Dakar).");
        }
    }

    // ---------- spécialités ----------

    @Transactional
    public SpecialtyResponse createSpecialty(UUID admin, SpecialtySave r) {
        if (r.code() == null || r.code().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CODE_REQUIS", "Le code de la spécialité est requis.");
        }
        if (specialties.existsByCode(r.code())) {
            throw new ApiException(HttpStatus.CONFLICT, "SPECIALITE_EXISTANTE", "Cette spécialité existe déjà.");
        }
        Specialty s = new Specialty();
        s.setCode(r.code());
        s.setNameFr(r.nameFr().trim());
        s.setNameEn(r.nameEn().trim());
        specialties.save(s);
        audit.record(admin, "SPECIALITE_AJOUTEE", "SPECIALTY", s.getCode(), s.getNameFr());
        return SpecialtyResponse.from(s);
    }

    @Transactional
    public SpecialtyResponse renameSpecialty(UUID admin, String code, SpecialtySave r) {
        Specialty s = specialties.findByCode(code)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SPECIALITE_INCONNUE", "Spécialité inconnue."));
        s.setNameFr(r.nameFr().trim());
        s.setNameEn(r.nameEn().trim());
        audit.record(admin, "SPECIALITE_MODIFIEE", "SPECIALTY", s.getCode(), s.getNameFr());
        return SpecialtyResponse.from(s);
    }

    // ---------- structures ----------

    @Transactional
    public FacilityResponse updateFacility(UUID admin, UUID id, FacilityRequest r) {
        Facility f = facilities.findById(id).orElseThrow(AdminPortalService::facilityNotFound);
        String cc = r.countryCode().toUpperCase(Locale.ROOT);
        if (!countries.existsById(cc)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYS_INCONNU", "Pays inconnu.");
        }
        f.setName(r.name().trim());
        f.setCountryCode(cc);
        f.setCity(r.city());
        f.setAddress(r.address());
        f.setPhone(r.phone());
        f.setLatitude(r.latitude());
        f.setLongitude(r.longitude());
        audit.record(admin, "STRUCTURE_MODIFIEE", "FACILITY", id, f.getName());
        return FacilityResponse.from(f);
    }

    @Transactional
    public void deleteFacility(UUID admin, UUID id) {
        Facility f = facilities.findById(id).orElseThrow(AdminPortalService::facilityNotFound);
        if (availabilityRules.existsByFacilityId(id) || appointments.existsByFacilityId(id)) {
            throw new ApiException(HttpStatus.CONFLICT, "STRUCTURE_UTILISEE",
                    "Cette structure a des horaires ou des rendez-vous : elle ne peut pas être supprimée.");
        }
        facilities.delete(f);
        audit.record(admin, "STRUCTURE_SUPPRIMEE", "FACILITY", id, f.getName());
    }

    private static ApiException facilityNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ETABLISSEMENT_INTROUVABLE", "Établissement introuvable.");
    }

    // ---------- utilisateurs ----------

    @Transactional(readOnly = true)
    public PageResponse<UserAdmin> patients(String q, int page, int size) {
        String like = q == null || q.isBlank() ? "" : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(patients.searchForAdmin(like, pageRequest).map(AdminPortalService::toUser));
    }

    private static UserAdmin toUser(Patient p) {
        User u = p.getUser();
        return new UserAdmin(u.getId(), u.getEmail(), u.getPhone(), u.getRole(), u.getStatus(), p.getFirstName(),
                p.getLastName(), p.getCountryCode(), u.getCreatedAt());
    }

    /** Suspendre ou réactiver un compte patient ou praticien (jamais un administrateur, jamais un compte supprimé). */
    @Transactional
    public void setUserStatus(UUID admin, UUID userId, UserStatus status) {
        if (status == UserStatus.DELETED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "STATUT_INVALIDE",
                    "La suppression se fait par le titulaire du compte.");
        }
        // D'abord fermer les sessions : cette requête vide le contexte JPA, le compte est donc lu ensuite
        // (dans l'autre ordre, le changement de statut serait perdu).
        if (status == UserStatus.SUSPENDED) refreshTokens.revokeAllForUser(userId);
        User u = users.findById(userId)
                .filter(x -> x.getStatus() != UserStatus.DELETED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "UTILISATEUR_INTROUVABLE",
                        "Utilisateur introuvable."));
        if (u.getRole() == Role.ADMIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ADMIN_NON_MODIFIABLE",
                    "Un compte administrateur ne peut pas être suspendu ici.");
        }
        u.setStatus(status); // l'accès en cours expire sous 15 min, sans renouvellement possible
        audit.record(admin, status == UserStatus.SUSPENDED ? "COMPTE_SUSPENDU" : "COMPTE_REACTIVE", "USER", userId,
                u.getRole().name());
    }

    // ---------- structures ----------

    @Transactional(readOnly = true)
    public PageResponse<StructureProfile> structures(VerificationStatus status, int page, int size) {
        PageRequest req = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("createdAt"));
        return PageResponse.of(organizationRepo.findByVerificationStatusAndUserStatusNot(status, UserStatus.DELETED, req)
                .map(StructureProfile::from));
    }

    @Transactional
    public StructureProfile verifyStructure(UUID admin, UUID id, VerificationStatus status) {
        Organization o = organizationRepo.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "STRUCTURE_INTROUVABLE", "Structure introuvable."));
        o.setVerificationStatus(status);
        audit.record(admin, "STRUCTURE_" + status.name(), "ORGANIZATION", id, o.getName());
        return StructureProfile.from(o);
    }

    @Transactional
    public StructureProfile structureCommission(UUID admin, UUID id, Integer percent) {
        Organization o = organizationRepo.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "STRUCTURE_INTROUVABLE", "Structure introuvable."));
        o.setCommissionPercent(percent);
        audit.record(admin, "STRUCTURE_COMMISSION", "ORGANIZATION", id,
                o.getName() + " : " + (percent == null ? "taux par défaut" : percent + " %"));
        return StructureProfile.from(o);
    }

    @Transactional
    public StructureProfile createStructure(UUID admin, StructureDtos.AdminStructureCreate r) {
        Organization o = authService.createStructureByAdmin(r);
        audit.record(admin, "STRUCTURE_AJOUTEE", "ORGANIZATION", o.getId(), o.getName());
        return StructureProfile.from(o);
    }

    /** Hors transaction : un e-mail qui échoue n'annule pas la création du compte. */
    public StructureDtos.StructureCreated welcomeStructure(StructureProfile s, String locale) {
        boolean sent = welcomeMail.send(s.email(), s.contactName(), "Structure", "Facility",
                " Vous pouvez aussi gérer votre structure depuis l'Espace Structure : https://ollisante.com/structure/",
                " You can also manage your facility from the Facility area: https://ollisante.com/structure/", locale);
        return new StructureDtos.StructureCreated(s, sent);
    }

    /** Modification de la fiche d'une structure (l'e-mail de connexion reste celui choisi à l'inscription). */
    @Transactional
    public StructureProfile updateStructure(UUID admin, UUID id, StructureUpdate r) {
        Organization o = organizationRepo.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "STRUCTURE_INTROUVABLE", "Structure introuvable."));
        o.setType(r.type());
        o.setName(r.name().trim());
        o.setCity(blank(r.city()));
        o.setAddress(blank(r.address()));
        String phone = r.phone().trim();
        o.setPhone(phone);
        User u = o.getUser();
        if (!phone.equals(u.getPhone()) && !users.existsByPhone(phone)) u.setPhone(phone);
        o.setDomain(r.domain() == null || r.domain().isBlank() ? null : r.domain().trim().toLowerCase(Locale.ROOT));
        o.setRegistrationNumber(r.registrationNumber().trim());
        o.setContactName(r.contactName().trim());
        o.setContactRole(blank(r.contactRole()));
        o.setBeds(r.beds());
        audit.record(admin, "STRUCTURE_MODIFIEE", "ORGANIZATION", id, o.getName());
        return StructureProfile.from(o);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    // ---------- fiche patient ----------

    private Patient patientOf(UUID userId) {
        return patients.findByUserId(userId)
                .filter(p -> p.getUser().getStatus() != UserStatus.DELETED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PATIENT_INTROUVABLE", "Patient introuvable."));
    }

    /** Consultations du patient, les plus récentes d'abord. Le motif (donnée médicale) n'est pas transmis. */
    @Transactional(readOnly = true)
    public List<PatientAppointment> patientAppointments(UUID userId) {
        Patient patient = patientOf(userId);
        return appointments.findByPatientIdOrderByStartsAtDesc(patient.getId()).stream().map(a -> {
            Payment pay = payments.findByAppointmentIdOrderByCreatedAtDesc(a.getId()).stream().findFirst().orElse(null);
            String org = a.getOrganizationId() == null ? null
                    : organizationRepo.findById(a.getOrganizationId()).map(Organization::getName).orElse(null);
            return new PatientAppointment(a.getId(), a.getStartsAt(), a.getStatus(), a.getMode(),
                    a.getPractitioner().getFirstName() + " " + a.getPractitioner().getLastName(),
                    a.getPractitioner().getSpecialties().stream().map(Specialty::getNameFr).sorted().toList(),
                    a.getFacility() == null ? null : a.getFacility().getName(), org, a.getPaymentMode(),
                    a.getPriceMinor(), a.getPriceCurrency(), pay == null ? null : pay.getStatus().name(),
                    pay == null ? null : pay.getOperator(), pay == null ? 0 : pay.getRefundedMinor());
        }).toList();
    }

    @Transactional
    public UserAdmin updatePatient(UUID admin, UUID userId, PatientUpdate r) {
        Patient p = patientOf(userId);
        User u = p.getUser();
        String email = r.email().trim().toLowerCase(Locale.ROOT);
        String phone = r.phone().trim();
        if ((!email.equals(u.getEmail()) && users.existsByEmail(email))
                || (!phone.equals(u.getPhone()) && users.existsByPhone(phone))) {
            throw new ApiException(HttpStatus.CONFLICT, "COMPTE_EXISTANT", "Un compte existe déjà avec cet identifiant.");
        }
        String country = r.countryCode() == null || r.countryCode().isBlank() ? null
                : r.countryCode().toUpperCase(Locale.ROOT);
        if (country != null && !countries.existsById(country)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYS_INCONNU", "Pays inconnu.");
        }
        u.setEmail(email);
        u.setPhone(phone);
        p.setFirstName(r.firstName().trim());
        p.setLastName(r.lastName().trim());
        p.setCountryCode(country);
        audit.record(admin, "PATIENT_MODIFIE", "USER", userId, p.getFirstName() + " " + p.getLastName());
        return toUser(p);
    }

    @Transactional
    public UserAdmin createPatient(UUID admin, PatientCreate r) {
        Patient p = authService.createPatientByAdmin(r.email(), r.phone(), r.firstName(), r.lastName(),
                r.countryCode(), r.locale(), r.password());
        audit.record(admin, "PATIENT_AJOUTE", "USER", p.getUser().getId(), p.getFirstName() + " " + p.getLastName());
        return toUser(p);
    }

    /** Hors transaction : un e-mail qui échoue n'annule pas la création du compte. */
    public PatientCreated welcomePatient(UserAdmin p, String locale) {
        boolean sent = welcomeMail.send(p.email(), p.firstName() + " " + p.lastName(), "Patient", "Patient", "", "",
                locale);
        return new PatientCreated(p, sent);
    }

    // ---------- journal ----------

    @Transactional(readOnly = true)
    public PageResponse<AuditEntry> audit(int page, int size) {
        return PageResponse.of(auditLog.findAllByOrderByCreatedAtDesc(
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))).map(AuditEntry::from));
    }
}
