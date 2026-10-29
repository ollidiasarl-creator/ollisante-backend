package com.ollisante.backend.repository;

import com.ollisante.backend.domain.Appointment;
import com.ollisante.backend.domain.AppointmentStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    List<Appointment> findByPatientIdOrderByStartsAtDesc(UUID patientId);

    List<Appointment> findByPractitionerIdOrderByStartsAtDesc(UUID practitionerId);

    @Query("select a.startsAt from Appointment a "
            + "where a.practitioner.id = :practitionerId and a.status in :statuses "
            + "and a.startsAt >= :from and a.startsAt < :to")
    List<Instant> findActiveStarts(@Param("practitionerId") UUID practitionerId,
                                   @Param("statuses") Collection<AppointmentStatus> statuses,
                                   @Param("from") Instant from,
                                   @Param("to") Instant to);

    @Modifying
    @Query("update Appointment a set a.status = :cancelled, a.cancelledReason = 'PAIEMENT_NON_RECU', "
            + "a.updatedAt = :now where a.status = :pending and a.createdAt < :cutoff")
    int expirePending(@Param("pending") AppointmentStatus pending,
                      @Param("cancelled") AppointmentStatus cancelled,
                      @Param("cutoff") Instant cutoff,
                      @Param("now") Instant now);

    long countByStartsAtGreaterThanEqualAndStartsAtLessThan(Instant from, Instant to);

    long countByStatusInAndStartsAtAfter(Collection<AppointmentStatus> statuses, Instant after);

    long countByStatusAndStartsAtGreaterThanEqualAndStartsAtLessThan(AppointmentStatus status, Instant from, Instant to);

    boolean existsByFacilityId(UUID facilityId);

    long countByPatientIdAndStatus(UUID patientId, AppointmentStatus status);

    long countByPatientIdAndStatusInAndStartsAtAfter(UUID patientId, Collection<AppointmentStatus> statuses, Instant after);

    boolean existsByPatientIdAndPractitionerIdAndStatusInAndStartsAtGreaterThanEqualAndStartsAtLessThan(
            UUID patientId, UUID practitionerId, Collection<AppointmentStatus> statuses, Instant from, Instant to);

    List<Appointment> findByOrganizationIdOrderByStartsAtDesc(UUID organizationId);

    /** Rendez-vous à rappeler : patient, médecin et lieu chargés d'un coup. */
    @Query("select a from Appointment a join fetch a.patient p join fetch p.user join fetch a.practitioner pr "
            + "join fetch pr.user left join fetch a.facility "
            + "where a.status = :status and a.startsAt > :from and a.startsAt <= :to order by a.startsAt")
    List<Appointment> findForReminders(@Param("status") AppointmentStatus status, @Param("from") Instant from,
                                       @Param("to") Instant to);
}
