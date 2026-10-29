package com.ollisante.backend.service;

import com.ollisante.backend.booking.TimeZones;
import com.ollisante.backend.common.ApiException;
import com.ollisante.backend.domain.AppointmentMode;
import com.ollisante.backend.domain.AvailabilityRule;
import com.ollisante.backend.domain.Facility;
import com.ollisante.backend.domain.OrganizationMember;
import com.ollisante.backend.domain.Practitioner;
import com.ollisante.backend.dto.AvailabilityRuleRequest;
import com.ollisante.backend.dto.AvailabilityRuleResponse;
import com.ollisante.backend.repository.AvailabilityRuleRepository;
import com.ollisante.backend.repository.FacilityRepository;
import com.ollisante.backend.repository.OrganizationMemberRepository;
import com.ollisante.backend.repository.PractitionerRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private final AvailabilityRuleRepository rules;
    private final PractitionerRepository practitioners;
    private final FacilityRepository facilities;
    private final TimeZones timeZones;
    private final OrganizationMemberRepository members;

    @Transactional(readOnly = true)
    public List<AvailabilityRuleResponse> list(UUID userId) {
        Practitioner practitioner = requirePractitioner(userId);
        return rules.findByPractitionerId(practitioner.getId()).stream()
                .sorted(Comparator.comparing(AvailabilityRule::getWeekday)
                        .thenComparing(AvailabilityRule::getStartTime))
                .map(AvailabilityRuleResponse::from)
                .toList();
    }

    @Transactional
    public AvailabilityRuleResponse create(UUID userId, AvailabilityRuleRequest r) {
        return createFor(requirePractitioner(userId), r);
    }

    /** Aussi utilisée par une structure pour les médecins qui lui sont rattachés. */
    @Transactional
    public AvailabilityRuleResponse createFor(Practitioner practitioner, AvailabilityRuleRequest r) {

        if (!r.endTime().isAfter(r.startTime())) {
            throw bad("HORAIRE_INVALIDE", "L'heure de fin doit être après l'heure de début.");
        }
        if (r.validFrom() != null && r.validUntil() != null && r.validUntil().isBefore(r.validFrom())) {
            throw bad("PERIODE_INVALIDE", "La date de fin de validité précède la date de début.");
        }
        if (r.mode() == AppointmentMode.VIDEO && !practitioner.isTeleconsultationEnabled()) {
            throw bad("TELECONSULTATION_DESACTIVEE", "La téléconsultation est désactivée pour ce praticien.");
        }
        Facility facility = null;
        if (r.mode() == AppointmentMode.IN_PERSON) {
            if (r.facilityId() == null) {
                throw bad("ETABLISSEMENT_REQUIS", "Un établissement est requis pour une consultation sur place.");
            }
            facility = facilities.findById(r.facilityId())
                    .orElseThrow(() -> bad("ETABLISSEMENT_INCONNU", "Établissement inconnu."));
            if (facility.getOrganizationId() != null && !members.existsByIdOrganizationIdAndIdPractitionerIdAndStatus(
                    facility.getOrganizationId(), practitioner.getId(), OrganizationMember.Status.ACTIVE)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "NON_MEMBRE_STRUCTURE",
                        "Ce site appartient à une structure dont vous n'êtes pas membre.");
            }
        }

        AvailabilityRule rule = new AvailabilityRule();
        rule.setPractitioner(practitioner);
        rule.setFacility(facility);
        rule.setWeekday(r.weekday());
        rule.setStartTime(r.startTime());
        rule.setEndTime(r.endTime());
        rule.setSlotMinutes(r.slotMinutes() == null ? 30 : r.slotMinutes());
        rule.setMode(r.mode());
        rule.setValidFrom(r.validFrom() != null
                ? r.validFrom()
                : LocalDate.now(timeZones.of(practitioner.getCountryCode())));
        rule.setValidUntil(r.validUntil());
        return AvailabilityRuleResponse.from(rules.save(rule));
    }

    @Transactional
    public void delete(UUID userId, UUID ruleId) {
        Practitioner practitioner = requirePractitioner(userId);
        AvailabilityRule rule = rules.findByIdAndPractitionerId(ruleId, practitioner.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PLAGE_INTROUVABLE",
                        "Plage de disponibilité introuvable."));
        rules.delete(rule);
    }

    private Practitioner requirePractitioner(UUID userId) {
        return practitioners.findByUserId(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "PROFIL_PRATICIEN_REQUIS",
                        "Réservé aux praticiens."));
    }

    private static ApiException bad(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}
