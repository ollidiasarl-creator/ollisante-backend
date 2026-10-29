package com.ollisante.backend.dto;

import com.ollisante.backend.domain.NewsAudience;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record NewsRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String body,
        @Size(max = 500) @Pattern(regexp = "^(/media/|https://).*", message = "Adresse d'image invalide")
        String imageUrl,
        @Size(max = 500) @Pattern(regexp = "^https?://.*", message = "Le lien doit commencer par https://")
        String linkUrl,
        @Size(max = 500) @Pattern(regexp = "^https?://.*", message = "Le lien vidéo doit commencer par https://")
        String videoUrl,
        @Size(min = 2, max = 2) String countryCode,
        NewsAudience audience) {
}
