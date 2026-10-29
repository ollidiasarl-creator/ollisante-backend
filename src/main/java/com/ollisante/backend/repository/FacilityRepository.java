package com.ollisante.backend.repository;

import com.ollisante.backend.domain.Facility;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FacilityRepository extends JpaRepository<Facility, UUID> {

    List<Facility> findByCountryCodeOrderByNameAsc(String countryCode);

    List<Facility> findByOrganizationIdOrderByNameAsc(UUID organizationId);
}
