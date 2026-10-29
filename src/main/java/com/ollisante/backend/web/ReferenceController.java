package com.ollisante.backend.web;

import com.ollisante.backend.dto.CountryResponse;
import com.ollisante.backend.dto.SpecialtyResponse;
import com.ollisante.backend.repository.CountryRepository;
import com.ollisante.backend.repository.SpecialtyRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Données de référence publiques (écrans d'inscription et de recherche). */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReferenceController {

    private final CountryRepository countries;
    private final SpecialtyRepository specialties;

    @GetMapping("/countries")
    public List<CountryResponse> countries() {
        return countries.findByActiveTrueOrderByNameFrAsc().stream().map(CountryResponse::from).toList();
    }

    @GetMapping("/specialties")
    public List<SpecialtyResponse> specialties() {
        return specialties.findAll(Sort.by("nameFr")).stream().map(SpecialtyResponse::from).toList();
    }
}
