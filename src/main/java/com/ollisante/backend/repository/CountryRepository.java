package com.ollisante.backend.repository;

import com.ollisante.backend.domain.Country;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CountryRepository extends JpaRepository<Country, String> {

    List<Country> findByActiveTrueOrderByNameFrAsc();
}
