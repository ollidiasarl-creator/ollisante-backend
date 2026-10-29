package com.ollisante.backend.service;

import com.ollisante.backend.booking.SlotService;
import com.ollisante.backend.booking.TimeZones;
import com.ollisante.backend.common.ApiException;
import com.ollisante.backend.domain.AppointmentMode;
import com.ollisante.backend.domain.AvailabilityRule;
import com.ollisante.backend.domain.Country;
import com.ollisante.backend.domain.Practitioner;
import com.ollisante.backend.domain.Specialty;
import com.ollisante.backend.domain.VerificationStatus;
import com.ollisante.backend.dto.PageResponse;
import com.ollisante.backend.dto.PractitionerSummaryResponse;
import com.ollisante.backend.dto.SlotResponse;
import com.ollisante.backend.repository.CountryRepository;
import com.ollisante.backend.repository.PractitionerRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PractitionerService {

    private static final int MAX_RANGE_DAYS = 31;

    private final PractitionerRepository practitioners;
    private final SlotService slotService;
    private final TimeZones timeZones;
    private final CountryRepository countries;

    @Transactional(readOnly = true)
    public PageResponse<PractitionerSummaryResponse> search(String countryCode, String specialty, String text,
                                                            Boolean teleconsultation, UUID facilityId,
                                                            int page, int size) {
        // Seuls les praticiens dont le diplôme a été vérifié sont visibles du public.
        // Un profil masqué après des signalements ne l'est plus qu'une fois vérifié par l'équipe.
        Specification<Practitioner> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("verificationStatus"), VerificationStatus.VERIFIED),
                cb.isFalse(root.get("hiddenByReports")));

        if (countryCode != null && !countryCode.isBlank()) {
            String cc = countryCode.trim().toUpperCase(Locale.ROOT);
            spec = spec.and((root, query, cb) -> cb.equal(root.get("countryCode"), cc));
        }
        if (teleconsultation != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("teleconsultationEnabled"), teleconsultation));
        }
        if (text != null && !text.isBlank()) {
            String like = "%" + text.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("firstName")), like),
                    cb.like(cb.lower(root.get("lastName")), like)));
        }
        if (specialty != null && !specialty.isBlank()) {
            String code = specialty.trim().toUpperCase(Locale.ROOT);
            spec = spec.and((root, query, cb) -> {
                Join<Practitioner, Specialty> join = root.join("specialties", JoinType.INNER);
                return cb.equal(join.get("code"), code);
            });
        }

        if (facilityId != null) {
            // Praticiens ayant au moins une plage encore valide dans cet établissement.
            LocalDate today = LocalDate.now();
            spec = spec.and((root, query, cb) -> {
                Subquery<UUID> sub = query.subquery(UUID.class);
                Root<AvailabilityRule> rule = sub.from(AvailabilityRule.class);
                sub.select(rule.get("practitioner").get("id")).where(
                        cb.equal(rule.get("facility").get("id"), facilityId),
                        cb.or(cb.isNull(rule.get("validUntil")),
                                cb.greaterThanOrEqualTo(rule.<LocalDate>get("validUntil"), today)));
                return root.get("id").in(sub);
            });
        }

        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0), Math.min(Math.max(size, 1), 50), Sort.by("lastName", "firstName"));
        Map<String, Country> byCode = countries.findAll().stream().collect(Collectors.toMap(Country::getCode, c -> c));
        Page<PractitionerSummaryResponse> result = practitioners.findAll(spec, pageRequest)
                .map(p -> PractitionerSummaryResponse.from(p, byCode.get(p.getCountryCode())));
        return PageResponse.of(result);
    }

    @Transactional(readOnly = true)
    public PractitionerSummaryResponse get(UUID id) {
        Practitioner p = requireVerified(id);
        return PractitionerSummaryResponse.from(p, countries.findById(p.getCountryCode()).orElse(null));
    }

    @Transactional(readOnly = true)
    public List<SlotResponse> slots(UUID id, LocalDate from, LocalDate to, AppointmentMode mode, UUID facilityId) {
        Practitioner practitioner = requireVerified(id);
        LocalDate start = from != null ? from : LocalDate.now(timeZones.of(practitioner.getCountryCode()));
        LocalDate end = to != null ? to : start.plusDays(6);
        if (end.isBefore(start) || ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PLAGE_INVALIDE",
                    "La plage de dates est invalide (maximum " + MAX_RANGE_DAYS + " jours).");
        }
        return slotService.freeSlots(practitioner, start, end, mode).stream()
                .filter(s -> facilityId == null || facilityId.equals(s.facilityId()))
                .map(SlotResponse::from).toList();
    }

    private Practitioner requireVerified(UUID id) {
        return practitioners.findById(id)
                .filter(p -> p.getVerificationStatus() == VerificationStatus.VERIFIED && !p.isHiddenByReports())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRATICIEN_INTROUVABLE",
                        "Praticien introuvable."));
    }
}
