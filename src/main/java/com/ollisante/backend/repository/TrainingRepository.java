package com.ollisante.backend.repository;

import com.ollisante.backend.domain.Training;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrainingRepository extends JpaRepository<Training, UUID> {

    Page<Training> findAllByOrderByStartsAtDesc(Pageable pageable);

    /** Formations à venir (ou en cours) qui concernent ce pays et l'une de ces spécialités. */
    @Query("""
            select t from Training t
            where t.published = true and coalesce(t.endsAt, t.startsAt) >= :now
              and (t.countryCode is null or t.countryCode = :country)
              and (t.specialtyCode is null or t.specialtyCode in :specialties)
            order by t.startsAt""")
    List<Training> upcomingFor(@Param("now") Instant now, @Param("country") String country,
                               @Param("specialties") Collection<String> specialties);
}
