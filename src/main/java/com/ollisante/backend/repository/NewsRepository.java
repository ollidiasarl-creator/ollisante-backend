package com.ollisante.backend.repository;

import com.ollisante.backend.domain.News;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface NewsRepository extends JpaRepository<News, UUID>, JpaSpecificationExecutor<News> {
}
