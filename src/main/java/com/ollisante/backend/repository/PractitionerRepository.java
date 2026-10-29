package com.ollisante.backend.repository;

import com.ollisante.backend.domain.Practitioner;
import com.ollisante.backend.domain.UserStatus;
import com.ollisante.backend.domain.VerificationStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PractitionerRepository
        extends JpaRepository<Practitioner, UUID>, JpaSpecificationExecutor<Practitioner> {

    Optional<Practitioner> findByUserId(UUID userId);

    /** Liste d'administration : sans les comptes supprimés par leur titulaire. */
    Page<Practitioner> findByVerificationStatusAndUserStatusNot(VerificationStatus status, UserStatus excluded,
                                                                 Pageable pageable);

    long countByVerificationStatusAndUserStatusNot(VerificationStatus status, UserStatus excluded);
}
