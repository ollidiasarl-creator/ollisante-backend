package com.ollisante.backend.repository;

import com.ollisante.backend.domain.Organization;
import com.ollisante.backend.domain.UserStatus;
import com.ollisante.backend.domain.VerificationStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    Optional<Organization> findByUserId(UUID userId);

    Page<Organization> findByVerificationStatusAndUserStatusNot(VerificationStatus status, UserStatus excluded,
                                                                Pageable pageable);
}
