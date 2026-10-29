package com.ollisante.backend.repository;

import com.ollisante.backend.domain.Role;
import com.ollisante.backend.domain.User;
import com.ollisante.backend.domain.UserStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    long countByRoleAndStatus(Role role, UserStatus status);
}
