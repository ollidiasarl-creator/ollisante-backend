package com.ollisante.backend.web;

import com.ollisante.backend.dto.FacilityRequest;
import com.ollisante.backend.dto.FacilityResponse;
import com.ollisante.backend.dto.SpecialtyResponse;
import com.ollisante.backend.service.FacilityService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import com.ollisante.backend.service.CountryScope;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/facilities")
@RequiredArgsConstructor
public class FacilityController {

    private final FacilityService service;
    private final CountryScope scope;

    @GetMapping
    public List<FacilityResponse> list(Authentication authentication,
                                       @RequestParam(required = false) String countryCode) {
        // Compte connecté : uniquement les établissements de son pays.
        return service.list(scope.of(authentication).orElse(countryCode));
    }

    @GetMapping("/{id}/specialties")
    public List<SpecialtyResponse> specialties(@PathVariable UUID id) {
        return service.specialties(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PRACTITIONER','ADMIN')")
    public FacilityResponse create(@Valid @RequestBody FacilityRequest request) {
        return service.create(request);
    }
}
