package com.ollisante.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/** Structure (hôpital, clinique, cabinet, association, ONG…) ; visible des patients une fois vérifiée par OLIDIA. */
@Entity
@Table(name = "organizations")
@Getter
@Setter
@NoArgsConstructor
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrganizationType type;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String countryCode;

    private String city;

    private String address;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String phone;

    private String domain;

    /** RCCM, NINEA, NIF, SIRET… selon le pays. */
    @Column(nullable = false)
    private String registrationNumber;

    @Column(nullable = false)
    private String contactName;

    private String contactRole;

    private Integer beds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    /** Commission OLIDIA propre à la structure ; vide = taux par défaut. */
    private Integer commissionPercent;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
