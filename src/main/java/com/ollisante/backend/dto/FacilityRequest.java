package com.ollisante.backend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record FacilityRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(min = 2, max = 2) String countryCode,
        @Size(max = 100) String city,
        @Size(max = 255) String address,
        @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Numéro au format international, ex. +22370000000")
        String phone,
        @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude) {
}
