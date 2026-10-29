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
import org.hibernate.annotations.CreationTimestamp;

/** Question fréquente ; la version anglaise est facultative (le français est affiché à défaut). */
@Entity
@Table(name = "faq_entries")
@Getter
@Setter
@NoArgsConstructor
public class FaqEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String questionFr;

    @Column(nullable = false)
    private String answerFr;

    private String questionEn;

    private String answerEn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NewsAudience audience = NewsAudience.ALL;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private boolean published = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
