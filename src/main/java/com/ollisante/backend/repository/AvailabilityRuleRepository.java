package com.ollisante.backend.repository;

import com.ollisante.backend.domain.AvailabilityRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvailabilityRuleRepository extends JpaRepository<AvailabilityRule, UUID> {

    List<AvailabilityRule> findByPractitionerId(UUID practitionerId);

    Optional<AvailabilityRule> findByIdAndPractitionerId(UUID id, UUID practitionerId);

    boolean existsByFacilityId(UUID facilityId);

    List<AvailabilityRule> findByFacilityOrganizationId(UUID organizationId);
}
