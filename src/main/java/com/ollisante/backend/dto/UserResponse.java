package com.ollisante.backend.dto;

import com.ollisante.backend.domain.Role;
import com.ollisante.backend.domain.User;
import java.util.UUID;

public record UserResponse(UUID id, String email, String phone, Role role, String locale) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getPhone(), user.getRole(), user.getLocale());
    }
}
