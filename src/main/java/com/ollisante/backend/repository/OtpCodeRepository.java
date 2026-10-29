package com.ollisante.backend.repository;

import com.ollisante.backend.domain.OtpCode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OtpCodeRepository extends JpaRepository<OtpCode, UUID> {

    Optional<OtpCode> findFirstByPhoneOrderByCreatedAtDesc(String phone);

    Optional<OtpCode> findFirstByPhoneAndConsumedFalseOrderByCreatedAtDesc(String phone);

    long countByPhoneAndCreatedAtAfter(String phone, Instant since);

    void deleteByPhone(String phone);
}
