package com.ollisante.backend.service;

import com.ollisante.backend.common.ApiException;
import com.ollisante.backend.domain.AppointmentMode;
import com.ollisante.backend.domain.AvailabilityRule;
import com.ollisante.backend.domain.Facility;
import com.ollisante.backend.domain.Organization;
import com.ollisante.backend.domain.OrganizationMember;
import com.ollisante.backend.domain.Practitioner;
import com.ollisante.backend.domain.Role;
import com.ollisante.backend.domain.User;
import com.ollisante.backend.domain.UserStatus;
import com.ollisante.backend.domain.VerificationStatus;
import com.ollisante.backend.dto.AppointmentResponse;
import com.ollisante.backend.dto.AvailabilityRuleRequest;
import com.ollisante.backend.dto.AvailabilityRuleResponse;
import com.ollisante.backend.dto.FacilityRequest;
import com.ollisante.backend.dto.FacilityResponse;
import com.ollisante.backend.dto.StructureDtos.Membership;
import com.ollisante.backend.dto.StructureDtos.MemberRow;
import com.ollisante.backend.dto.StructureDtos.ProfileUpdate;
import com.ollisante.backend.dto.StructureDtos.StructureProfile;
import com.ollisante.backend.repository.AppointmentRepository;
import com.ollisante.backend.repository.AvailabilityRuleRepository;
import com.ollisante.backend.repository.FacilityRepository;
import com.ollisante.backend.repository.OrganizationMemberRepository;
import com.ollisante.backend.repository.OrganizationRepository;
import com.ollisante.backend.repository.PractitionerRepository;
import com.ollisante.backend.repository.UserRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Espace Structure : profil, sites, médecins rattachés, horaires et rendez-vous de la structure. */
@Service
@RequiredArgsConstructor
public class StructureService {

    private final OrganizationRepository organizations;
    private final OrganizationMemberRepository members;
    private final PractitionerRepository practitioners;
    private final UserRepository users;
    private final FacilityRepository facilities;
    private final AvailabilityRuleRepository rules;
    private final AvailabilityService availability;
    private final AppointmentRepository appointments;
    private final AppointmentService appointmentService;

    /** Structure du compte connecté. */
    public Organization require(UUID userId) {
        return organizations.findByUserId(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "PROFIL_STRUCTURE_REQUIS",
                        "Réservé aux structures."));
    }

    /** Les actions qui touchent les patients attendent la vérification de la structure par OLIDIA. */
    private Organization requireVerified(UUID userId) {
        Organization o = require(userId);
        if (o.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "STRUCTURE_NON_VERIFIEE",
                    "Votre structure doit d'abord être vérifiée par OLIDIA.");
        }
        return o;
    }

    // ---------- profil et sites ----------

    @Transactional(readOnly = true)
    public StructureProfile profile(UUID userId) {
        return StructureProfile.from(require(userId));
    }

    @Transactional
    public StructureProfile update(UUID userId, ProfileUpdate r) {
        Organization o = require(userId);
        if (r.city() != null) o.setCity(blank(r.city()));
        if (r.address() != null) o.setAddress(blank(r.address()));
        if (r.phone() != null && !r.phone().isBlank()) {
            String phone = r.phone().trim();
            o.setPhone(phone);
            // Garde le numéro de connexion aligné sur celui de la structure, s'il est libre.
            User user = o.getUser();
            if (!phone.equals(user.getPhone()) && !users.existsByPhone(phone)) user.setPhone(phone);
        }
        if (r.domain() != null) o.setDomain(r.domain().isBlank() ? null : r.domain().trim().toLowerCase(Locale.ROOT));
        if (r.contactName() != null && !r.contactName().isBlank()) o.setContactName(r.contactName().trim());
        if (r.contactRole() != null) o.setContactRole(blank(r.contactRole()));
        if (r.beds() != null) o.setBeds(r.beds());
        return StructureProfile.from(o);
    }

    @Transactional(readOnly = true)
    public List<FacilityResponse> sites(UUID userId) {
        Organization o = require(userId);
        return facilities.findByOrganizationIdOrderByNameAsc(o.getId()).stream()
                .map(f -> FacilityResponse.from(f, o.getType())).toList();
    }

    @Transactional
    public FacilityResponse addSite(UUID userId, FacilityRequest r) {
        Organization o = require(userId);
        Facility f = new Facility();
        f.setName(r.name().trim());
        f.setCountryCode(o.getCountryCode()); // un site est dans le pays de la structure
        f.setCity(r.city());
        f.setAddress(r.address());
        f.setPhone(r.phone());
        f.setLatitude(r.latitude());
        f.setLongitude(r.longitude());
        f.setOrganizationId(o.getId());
        return FacilityResponse.from(facilities.save(f), o.getType());
    }

    // ---------- médecins ----------

    @Transactional(readOnly = true)
    public List<MemberRow> members(UUID userId) {
        return members.findByIdOrganizationIdOrderByCreatedAtDesc(require(userId).getId()).stream()
                .filter(m -> m.getPractitioner().getUser().getStatus() != UserStatus.DELETED)
                .map(MemberRow::from).toList();
    }

    /** Invite un médecin déjà inscrit sur Ollisanté (par l'e-mail de son compte) ; il accepte dans l'application. */
    @Transactional
    public MemberRow invite(UUID userId, String email) {
        Organization o = requireVerified(userId);
        Practitioner p = users.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .filter(u -> u.getRole() == Role.PRACTITIONER && u.getStatus() == UserStatus.ACTIVE)
                .flatMap(u -> practitioners.findByUserId(u.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEDECIN_INTROUVABLE",
                        "Aucun professionnel inscrit avec cette adresse. Il doit d'abord créer son compte dans l'application."));
        if (!p.getCountryCode().equals(o.getCountryCode())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYS_DIFFERENT",
                    "Ce professionnel exerce dans un autre pays que votre structure.");
        }
        OrganizationMember.Key key = new OrganizationMember.Key(o.getId(), p.getId());
        OrganizationMember m = members.findById(key).orElseGet(() -> {
            OrganizationMember created = new OrganizationMember();
            created.setId(key);
            created.setOrganization(o);
            created.setPractitioner(p);
            return members.saveAndFlush(created);
        });
        return MemberRow.from(m);
    }

    /** Retire le médecin : ses horaires dans les sites de la structure sont supprimés. */
    @Transactional
    public void removeMember(UUID userId, UUID practitionerId) {
        Organization o = require(userId);
        OrganizationMember m = members.findById(new OrganizationMember.Key(o.getId(), practitionerId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEMBRE_INTROUVABLE", "Médecin introuvable."));
        rules.deleteAll(rules.findByFacilityOrganizationId(o.getId()).stream()
                .filter(r -> r.getPractitioner().getId().equals(practitionerId)).toList());
        members.delete(m);
    }

    // ---------- côté médecin : invitations ----------

    @Transactional(readOnly = true)
    public List<Membership> memberships(UUID practitionerUserId) {
        Practitioner p = practitioner(practitionerUserId);
        return members.findByIdPractitionerIdOrderByCreatedAtDesc(p.getId()).stream()
                .filter(m -> m.getOrganization().getVerificationStatus() == VerificationStatus.VERIFIED)
                .map(Membership::from).toList();
    }

    @Transactional
    public Membership answer(UUID practitionerUserId, UUID organizationId, boolean accept) {
        Practitioner p = practitioner(practitionerUserId);
        OrganizationMember m = members.findById(new OrganizationMember.Key(organizationId, p.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INVITATION_INTROUVABLE",
                        "Invitation introuvable."));
        if (accept) {
            m.setStatus(OrganizationMember.Status.ACTIVE);
            return Membership.from(m);
        }
        // Refus ou départ : on retire aussi ses horaires dans les sites de la structure.
        rules.deleteAll(rules.findByFacilityOrganizationId(organizationId).stream()
                .filter(r -> r.getPractitioner().getId().equals(p.getId())).toList());
        Membership gone = Membership.from(m);
        members.delete(m);
        return gone;
    }

    private Practitioner practitioner(UUID userId) {
        return practitioners.findByUserId(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "PROFIL_PRATICIEN_REQUIS",
                        "Réservé aux praticiens."));
    }

    // ---------- horaires des médecins dans la structure ----------

    public record RuleRow(UUID practitionerId, String practitionerName, AvailabilityRuleResponse rule) {
    }

    @Transactional(readOnly = true)
    public List<RuleRow> rules(UUID userId) {
        Organization o = require(userId);
        return rules.findByFacilityOrganizationId(o.getId()).stream()
                .sorted(Comparator.comparing((AvailabilityRule r) -> r.getPractitioner().getLastName())
                        .thenComparing(AvailabilityRule::getWeekday).thenComparing(AvailabilityRule::getStartTime))
                .map(r -> new RuleRow(r.getPractitioner().getId(),
                        r.getPractitioner().getFirstName() + " " + r.getPractitioner().getLastName(),
                        AvailabilityRuleResponse.from(r)))
                .toList();
    }

    @Transactional
    public AvailabilityRuleResponse addRule(UUID userId, UUID practitionerId, AvailabilityRuleRequest r) {
        Organization o = requireVerified(userId);
        if (r.mode() != AppointmentMode.IN_PERSON || r.facilityId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ETABLISSEMENT_REQUIS", "Choisissez un site de la structure.");
        }
        Facility f = facilities.findById(r.facilityId()).filter(x -> o.getId().equals(x.getOrganizationId()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "ETABLISSEMENT_INCONNU",
                        "Ce site n'appartient pas à votre structure."));
        Practitioner p = practitioners.findById(practitionerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEMBRE_INTROUVABLE", "Médecin introuvable."));
        return availability.createFor(p, r); // vérifie aussi que le médecin est membre actif
    }

    @Transactional
    public void deleteRule(UUID userId, UUID ruleId) {
        Organization o = require(userId);
        AvailabilityRule rule = rules.findById(ruleId)
                .filter(r -> r.getFacility() != null && o.getId().equals(r.getFacility().getOrganizationId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PLAGE_INTROUVABLE", "Plage introuvable."));
        rules.delete(rule);
    }

    // ---------- rendez-vous ----------

    @Transactional(readOnly = true)
    public List<AppointmentResponse> appointments(UUID userId) {
        return appointments.findByOrganizationIdOrderByStartsAtDesc(require(userId).getId()).stream()
                .map(AppointmentResponse::from).toList();
    }

    /** Annulation par la structure : le patient est remboursé intégralement. */
    @Transactional
    public AppointmentResponse cancel(UUID userId, UUID appointmentId, String reason) {
        return appointmentService.cancel(userId, Role.STRUCTURE, appointmentId, reason);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
