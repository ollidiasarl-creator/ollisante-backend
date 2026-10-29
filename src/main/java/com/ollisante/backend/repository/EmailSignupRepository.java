package com.ollisante.backend.repository;

import com.ollisante.backend.domain.EmailSignup;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailSignupRepository extends JpaRepository<EmailSignup, UUID> {

    Optional<EmailSignup> findFirstByEmailOrderByCreatedAtDesc(String email);

    Optional<EmailSignup> findFirstByEmailAndConsumedFalseOrderByCreatedAtDesc(String email);

    long countByEmailAndCreatedAtAfter(String email, Instant since);
}
