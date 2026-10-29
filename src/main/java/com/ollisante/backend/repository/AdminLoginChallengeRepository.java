package com.ollisante.backend.repository;

import com.ollisante.backend.domain.AdminLoginChallenge;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminLoginChallengeRepository extends JpaRepository<AdminLoginChallenge, UUID> {

    long countByUserIdAndCreatedAtAfter(UUID userId, Instant since);
}
