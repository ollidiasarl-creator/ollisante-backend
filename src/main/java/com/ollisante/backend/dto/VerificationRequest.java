package com.ollisante.backend.dto;

import com.ollisante.backend.domain.VerificationStatus;
import jakarta.validation.constraints.NotNull;

public record VerificationRequest(@NotNull VerificationStatus status) {
}
