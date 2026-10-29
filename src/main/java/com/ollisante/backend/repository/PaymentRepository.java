package com.ollisante.backend.repository;

import com.ollisante.backend.domain.Payment;
import com.ollisante.backend.domain.PaymentStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /** Verrou : le rappel de PawaPay et le contrôle de l'application peuvent arriver en même temps. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> lockById(@Param("id") UUID id);

    List<Payment> findByAppointmentIdOrderByCreatedAtDesc(UUID appointmentId);

    List<Payment> findByStatusAndCreatedAtBetween(PaymentStatus status, Instant from, Instant to);

    Page<Payment> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Paiements réussis des consultations passées, pas encore reversés au praticien. */
    @Query("""
            select p from Payment p join fetch p.appointment a join fetch a.practitioner
            where p.status in :statuses and p.payoutId is null and a.startsAt < :before
              and not exists (select r from Refund r where r.payment = p
                              and r.status = com.ollisante.backend.domain.Refund.Status.PENDING)""")
    List<Payment> eligibleForPayout(@Param("statuses") Collection<PaymentStatus> statuses,
                                    @Param("before") Instant before);

    /** Rubrique « Paiements et reçus » du patient : ses paiements Mobile Money (sauf les tentatives échouées). */
    @Query("""
            select p from Payment p join fetch p.appointment a join fetch a.practitioner left join fetch a.facility
            where p.payerUserId = :userId and p.status <> com.ollisante.backend.domain.PaymentStatus.FAILED
            order by p.createdAt desc""")
    List<Payment> findForPayer(@Param("userId") UUID userId);

    /** Encaissements d'un médecin (paiements réussis ou remboursés), les plus récents d'abord. */
    @Query("""
            select p from Payment p join fetch p.appointment a join fetch a.patient
            where a.practitioner.id = :practitionerId and p.status in :statuses order by p.createdAt desc""")
    List<Payment> findForPractitioner(@Param("practitionerId") UUID practitionerId,
                                      @Param("statuses") Collection<PaymentStatus> statuses, Pageable pageable);

    /** Encaissements des rendez-vous pris dans les lieux d'une structure. */
    @Query("""
            select p from Payment p join fetch p.appointment a join fetch a.patient join fetch a.practitioner
            where a.organizationId = :organizationId and p.status in :statuses order by p.createdAt desc""")
    List<Payment> findForOrganization(@Param("organizationId") UUID organizationId,
                                      @Param("statuses") Collection<PaymentStatus> statuses, Pageable pageable);

    @Query("""
            select p from Payment p join fetch p.appointment
            where p.status in :statuses and p.createdAt >= :from and p.createdAt < :to""")
    List<Payment> collectedBetween(@Param("statuses") Collection<PaymentStatus> statuses, @Param("from") Instant from,
                                   @Param("to") Instant to);

    Page<Payment> findByAppointmentOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);
}
