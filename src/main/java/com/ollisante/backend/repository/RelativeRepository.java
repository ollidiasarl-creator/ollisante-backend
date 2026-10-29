package com.ollisante.backend.repository;

import com.ollisante.backend.domain.Relative;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RelativeRepository extends JpaRepository<Relative, UUID> {

    List<Relative> findByOwnerUserIdOrderByCreatedAtAsc(UUID ownerUserId);

    Optional<Relative> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
}
