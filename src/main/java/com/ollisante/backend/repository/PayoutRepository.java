package com.ollisante.backend.repository;

import com.ollisante.backend.domain.Payout;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayoutRepository extends JpaRepository<Payout, UUID> {

    Page<Payout> findAllByOrderByPeriodEndDescCreatedAtDesc(Pageable pageable);

    Page<Payout> findByStatusOrderByPeriodEndDescCreatedAtDesc(Payout.Status status, Pageable pageable);

    Page<Payout> findByOrganizationIdOrderByPeriodEndDescCreatedAtDesc(UUID organizationId, Pageable pageable);

    Page<Payout> findByPractitionerIdOrderByPeriodEndDescCreatedAtDesc(UUID practitionerId, Pageable pageable);
}
