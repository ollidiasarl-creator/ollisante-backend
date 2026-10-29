package com.ollisante.backend.security;

import com.ollisante.backend.domain.Role;
import java.util.UUID;

public record TokenData(UUID userId, Role role) {
}
