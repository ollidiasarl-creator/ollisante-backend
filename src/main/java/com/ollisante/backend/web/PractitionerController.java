package com.ollisante.backend.web;

import com.ollisante.backend.domain.AppointmentMode;
import com.ollisante.backend.dto.PageResponse;
import com.ollisante.backend.dto.PractitionerSummaryResponse;
import com.ollisante.backend.dto.SlotResponse;
import com.ollisante.backend.service.PractitionerService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import com.ollisante.backend.common.ApiException;
import com.ollisante.backend.service.CountryScope;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Consultation publique (sans connexion) : recherche, fiche et créneaux libres d'un praticien. */
@RestController
@RequestMapping("/api/v1/practitioners")
@RequiredArgsConstructor
public class PractitionerController {

    private final PractitionerService service;
    private final CountryScope scope;

    @GetMapping
    public PageResponse<PractitionerSummaryResponse> search(
            Authentication authentication,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String specialty,
            @RequestParam(required = false, name = "q") String text,
            @RequestParam(required = false) Boolean teleconsultation,
            @RequestParam(required = false) UUID facilityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Compte connecté : uniquement les professionnels de son pays, quel que soit le pays demandé.
        String country = scope.of(authentication).orElse(countryCode);
        return service.search(country, specialty, text, teleconsultation, facilityId, page, size);
    }

    @GetMapping("/{id}")
    public PractitionerSummaryResponse get(Authentication authentication, @PathVariable UUID id) {
        PractitionerSummaryResponse p = service.get(id);
        requireSameCountry(authentication, p.countryCode());
        return p;
    }

    @GetMapping("/{id}/slots")
    public List<SlotResponse> slots(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) AppointmentMode mode,
            @RequestParam(required = false) UUID facilityId,
            Authentication authentication) {
        requireSameCountry(authentication, service.get(id).countryCode());
        return service.slots(id, from, to, mode, facilityId);
    }

    /** Un compte ne voit que les professionnels (fiche et disponibilités) de son pays. */
    private void requireSameCountry(Authentication authentication, String practitionerCountry) {
        scope.of(authentication).ifPresent(mine -> {
            if (!mine.equalsIgnoreCase(practitionerCountry)) {
                throw new ApiException(HttpStatus.NOT_FOUND, "HORS_PAYS",
                        "Ce professionnel n'exerce pas dans votre pays.");
            }
        });
    }
}
