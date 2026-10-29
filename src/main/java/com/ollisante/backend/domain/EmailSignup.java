package com.ollisante.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/** Inscription patient par e-mail en attente du code de confirmation. */
@Entity
@Table(name = "email_signups")
@Getter
@Setter
@NoArgsConstructor
public class EmailSignup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String email;

    /** Mot de passe déjà chiffré (BCrypt), recopié tel quel dans le compte à la confirmation. */
    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    private String countryCode;

    private LocalDate birthDate;

    private String locale;

    /** Jamais le code en clair : seulement son empreinte HMAC. */
    @Column(nullable = false)
    private String codeHash;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private boolean consumed;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
