package com.ollisante.backend.repository;

import com.ollisante.backend.domain.OrganizationMember;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, OrganizationMember.Key> {

    List<OrganizationMember> findByIdOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    List<OrganizationMember> findByIdPractitionerIdOrderByCreatedAtDesc(UUID practitionerId);

    boolean existsByIdOrganizationIdAndIdPractitionerIdAndStatus(UUID organizationId, UUID practitionerId,
                                                                 OrganizationMember.Status status);
}
