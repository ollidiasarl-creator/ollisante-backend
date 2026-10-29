package com.ollisante.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Actualité santé. countryCode vide = concerne toute l'Afrique. */
@Entity
@Table(name = "news")
@Getter
@Setter
@NoArgsConstructor
public class News {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    private String body;

    private String imageUrl;

    private String linkUrl;

    private String videoUrl;

    private String countryCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NewsAudience audience = NewsAudience.ALL;

    private UUID authorUserId;

    @Column(nullable = false)
    private Instant publishedAt;
}
