package com.ollisante.backend.repository;

import com.ollisante.backend.domain.AdminAuditEntry;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditRepository extends JpaRepository<AdminAuditEntry, UUID> {

    Page<AdminAuditEntry> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
