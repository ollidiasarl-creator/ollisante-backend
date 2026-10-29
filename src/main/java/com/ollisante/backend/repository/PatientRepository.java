package com.ollisante.backend.repository;

import com.ollisante.backend.domain.Patient;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatientRepository extends JpaRepository<Patient, UUID> {

    Optional<Patient> findByUserId(UUID userId);

    /** Liste d'administration : recherche sur le nom, l'e-mail ou le téléphone (comptes supprimés exclus). */
    @Query("""
            select p from Patient p join p.user u
            where u.status <> com.ollisante.backend.domain.UserStatus.DELETED
              and (:q = '' or lower(p.firstName) like :q or lower(p.lastName) like :q
                   or lower(u.email) like :q or u.phone like :q)""")
    Page<Patient> searchForAdmin(@Param("q") String likeOrEmpty, Pageable pageable);
}
