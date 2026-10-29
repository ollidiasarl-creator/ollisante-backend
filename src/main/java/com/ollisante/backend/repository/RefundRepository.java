package com.ollisante.backend.repository;

import com.ollisante.backend.domain.Refund;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Refund r where r.id = :id")
    Optional<Refund> lockById(@Param("id") UUID id);

    List<Refund> findByStatus(Refund.Status status);

    List<Refund> findByPaymentIdOrderByCreatedAtDesc(UUID paymentId);
}
