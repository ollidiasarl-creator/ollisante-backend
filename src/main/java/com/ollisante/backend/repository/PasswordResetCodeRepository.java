package com.ollisante.backend.repository;

import com.ollisante.backend.domain.PasswordResetCode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, UUID> {

    Optional<PasswordResetCode> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<PasswordResetCode> findFirstByUserIdAndConsumedFalseOrderByCreatedAtDesc(UUID userId);

    long countByUserIdAndCreatedAtAfter(UUID userId, Instant since);

    void deleteByUserId(UUID userId);
}
