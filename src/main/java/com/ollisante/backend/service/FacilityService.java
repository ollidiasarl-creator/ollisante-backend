package com.ollisante.backend.service;

import com.ollisante.backend.booking.TimeZones;
import com.ollisante.backend.common.ApiException;
import com.ollisante.backend.domain.Facility;
import com.ollisante.backend.domain.OrganizationType;
import com.ollisante.backend.domain.UserStatus;
import com.ollisante.backend.domain.VerificationStatus;
import com.ollisante.backend.dto.FacilityRequest;
import com.ollisante.backend.dto.FacilityResponse;
import com.ollisante.backend.dto.SpecialtyResponse;
import com.ollisante.backend.repository.CountryRepository;
import com.ollisante.backend.repository.FacilityRepository;
import com.ollisante.backend.repository.OrganizationRepository;
import com.ollisante.backend.repository.SpecialtyRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FacilityService {

    private final FacilityRepository facilities;
    private final CountryRepository countries;
    private final SpecialtyRepository specialties;
    private final TimeZones timeZones;
    private final OrganizationRepository organizations;

    @Transactional(readOnly = true)
    public List<FacilityResponse> list(String countryCode) {
        List<Facility> all = countryCode == null || countryCode.isBlank() ? facilities.findAll(Sort.by("name"))
                : facilities.findByCountryCodeOrderByNameAsc(countryCode.trim().toUpperCase(Locale.ROOT));
        // Les sites d'une structure n'apparaissent qu'une fois la structure vérifiée par OLIDIA.
        Map<UUID, OrganizationType> verified = new HashMap<>();
        organizations.findAll().stream()
                .filter(o -> o.getVerificationStatus() == VerificationStatus.VERIFIED
                        && o.getUser().getStatus() == UserStatus.ACTIVE)
                .forEach(o -> verified.put(o.getId(), o.getType()));
        return all.stream()
                .filter(f -> f.getOrganizationId() == null || verified.containsKey(f.getOrganizationId()))
                .map(f -> FacilityResponse.from(f, f.getOrganizationId() == null ? null : verified.get(f.getOrganizationId())))
                .toList();
    }

    /** Spécialités qu'un patient peut réserver dans cet établissement (selon les praticiens qui y consultent). */
    @Transactional(readOnly = true)
    public List<SpecialtyResponse> specialties(UUID facilityId) {
        Facility facility = facilities.findById(facilityId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ETABLISSEMENT_INTROUVABLE",
                        "Établissement introuvable."));
        LocalDate today = LocalDate.now(timeZones.of(facility.getCountryCode()));
        return specialties.offeredAt(facilityId, VerificationStatus.VERIFIED, today).stream()
                .map(SpecialtyResponse::from).toList();
    }

    @Transactional
    public FacilityResponse create(FacilityRequest r) {
        String cc = r.countryCode().toUpperCase(Locale.ROOT);
        if (!countries.existsById(cc)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYS_INCONNU", "Pays inconnu.");
        }
        Facility facility = new Facility();
        facility.setName(r.name().trim());
        facility.setCountryCode(cc);
        facility.setCity(r.city());
        facility.setAddress(r.address());
        facility.setPhone(r.phone());
        facility.setLatitude(r.latitude());
        facility.setLongitude(r.longitude());
        return FacilityResponse.from(facilities.save(facility));
    }
}
