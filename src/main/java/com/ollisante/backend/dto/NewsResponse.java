package com.ollisante.backend.dto;

import com.ollisante.backend.domain.News;
import com.ollisante.backend.domain.NewsAudience;
import java.time.Instant;
import java.util.UUID;

public record NewsResponse(UUID id, String title, String body, String imageUrl, String linkUrl, String videoUrl,
                           String countryCode, NewsAudience audience, Instant publishedAt) {

    public static NewsResponse from(News n) {
        return new NewsResponse(n.getId(), n.getTitle(), n.getBody(), n.getImageUrl(), n.getLinkUrl(),
                n.getVideoUrl(), n.getCountryCode(), n.getAudience(), n.getPublishedAt());
    }
}
