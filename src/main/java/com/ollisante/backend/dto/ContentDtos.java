package com.ollisante.backend.dto;

import com.ollisante.backend.domain.FaqEntry;
import com.ollisante.backend.domain.NewsAudience;
import com.ollisante.backend.domain.Training;
import com.ollisante.backend.domain.TrainingFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Formations (professionnels) et FAQ. */
public final class ContentDtos {

    private ContentDtos() {
    }

    public record TrainingRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 5000) String description,
            @NotNull TrainingFormat format,
            @NotNull Instant startsAt,
            Instant endsAt,
            @Size(max = 255) String location,
            @Size(max = 500) @Pattern(regexp = "^https?://.*", message = "Le lien doit commencer par https://")
            String linkUrl,
            @Size(min = 2, max = 2) String countryCode,
            @Size(max = 50) String specialtyCode,
            Boolean published) {
    }

    public record TrainingResponse(UUID id, String title, String description, TrainingFormat format, Instant startsAt,
                                   Instant endsAt, String location, String linkUrl, String countryCode,
                                   String specialtyCode, boolean published) {
        public static TrainingResponse from(Training t) {
            return new TrainingResponse(t.getId(), t.getTitle(), t.getDescription(), t.getFormat(), t.getStartsAt(),
                    t.getEndsAt(), t.getLocation(), t.getLinkUrl(), t.getCountryCode(), t.getSpecialtyCode(),
                    t.isPublished());
        }
    }

    public record FaqRequest(
            @NotBlank @Size(max = 300) String questionFr,
            @NotBlank @Size(max = 5000) String answerFr,
            @Size(max = 300) String questionEn,
            @Size(max = 5000) String answerEn,
            NewsAudience audience,
            Integer position,
            Boolean published) {
    }

    public record FaqResponse(UUID id, String questionFr, String answerFr, String questionEn, String answerEn,
                              NewsAudience audience, int position, boolean published) {
        public static FaqResponse from(FaqEntry f) {
            return new FaqResponse(f.getId(), f.getQuestionFr(), f.getAnswerFr(), f.getQuestionEn(), f.getAnswerEn(),
                    f.getAudience(), f.getPosition(), f.isPublished());
        }
    }
}
