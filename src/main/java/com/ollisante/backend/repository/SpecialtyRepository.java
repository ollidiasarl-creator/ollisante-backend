package com.ollisante.backend.repository;

import com.ollisante.backend.domain.Specialty;
import com.ollisante.backend.domain.VerificationStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpecialtyRepository extends JpaRepository<Specialty, UUID> {

    List<Specialty> findByCodeIn(Collection<String> codes);

    /** Spécialités des praticiens vérifiés qui ont des disponibilités en cours dans cet établissement. */
    @Query("""
            select distinct s from AvailabilityRule r join r.practitioner p join p.specialties s
            where r.facility.id = :facilityId and p.verificationStatus = :verified
              and (r.validUntil is null or r.validUntil >= :today)
            order by s.nameFr""")
    List<Specialty> offeredAt(@Param("facilityId") UUID facilityId, @Param("verified") VerificationStatus verified,
                              @Param("today") LocalDate today);

    Optional<Specialty> findByCode(String code);

    boolean existsByCode(String code);
}
