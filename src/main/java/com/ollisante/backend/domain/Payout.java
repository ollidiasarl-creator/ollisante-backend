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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/** Somme due à un praticien pour ses consultations payées d'une période (reversement manuel). */
@Entity
@Table(name = "payouts")
@Getter
@Setter
@NoArgsConstructor
public class Payout {

    public enum Status { DUE, PAID }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Bénéficiaire : le praticien indépendant… */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "practitioner_id")
    private Practitioner practitioner;

    /** … ou la structure. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    private Integer commissionPercent;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private long grossMinor;

    @Column(nullable = false)
    private long commissionMinor;

    @Column(nullable = false)
    private long amountMinor;

    @Column(nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DUE;

    private Instant paidAt;

    private String reference;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
