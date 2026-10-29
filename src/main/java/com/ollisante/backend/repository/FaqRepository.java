package com.ollisante.backend.repository;

import com.ollisante.backend.domain.FaqEntry;
import com.ollisante.backend.domain.NewsAudience;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaqRepository extends JpaRepository<FaqEntry, UUID> {

    List<FaqEntry> findAllByOrderByPositionAscCreatedAtAsc();

    List<FaqEntry> findByPublishedTrueAndAudienceInOrderByPositionAscCreatedAtAsc(Collection<NewsAudience> audiences);
}
