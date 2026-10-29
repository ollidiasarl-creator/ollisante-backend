package com.ollisante.backend.service;

import com.ollisante.backend.common.ApiException;
import com.ollisante.backend.domain.News;
import com.ollisante.backend.domain.NewsAudience;
import com.ollisante.backend.domain.Role;
import com.ollisante.backend.dto.NewsRequest;
import com.ollisante.backend.dto.NewsResponse;
import com.ollisante.backend.dto.PageResponse;
import com.ollisante.backend.repository.CountryRepository;
import com.ollisante.backend.repository.NewsRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NewsService {

    private final NewsRepository news;
    private final CountryRepository countries;
    private final AuditService audit;

    /**
     * Actualités visibles par ce rôle, les plus récentes d'abord. Avec un pays : celles de ce pays
     * et celles qui concernent toute l'Afrique. L'administrateur voit tout.
     */
    @Transactional(readOnly = true)
    public PageResponse<NewsResponse> list(Role role, String countryCode, int page, int size) {
        Specification<News> spec = (root, query, cb) -> cb.conjunction();
        if (role != Role.ADMIN) {
            NewsAudience own = role == Role.PRACTITIONER || role == Role.STRUCTURE
                    ? NewsAudience.PRACTITIONER : NewsAudience.PATIENT;
            spec = spec.and((root, query, cb) -> root.get("audience").in(NewsAudience.ALL, own));
        }
        if (countryCode != null && !countryCode.isBlank()) {
            String cc = countryCode.trim().toUpperCase(Locale.ROOT);
            spec = spec.and((root, query, cb) -> cb.or(cb.isNull(root.get("countryCode")),
                    cb.equal(root.get("countryCode"), cc)));
        }
        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0), Math.min(Math.max(size, 1), 50), Sort.by(Sort.Direction.DESC, "publishedAt"));
        return PageResponse.of(news.findAll(spec, pageRequest).map(NewsResponse::from));
    }

    @Transactional
    public NewsResponse create(UUID authorUserId, NewsRequest r) {
        News n = new News();
        apply(n, r);
        n.setAuthorUserId(authorUserId);
        n.setPublishedAt(Instant.now());
        news.save(n);
        audit.record(authorUserId, "ACTUALITE_PUBLIEE", "NEWS", n.getId(), n.getTitle());
        return NewsResponse.from(n);
    }

    @Transactional
    public NewsResponse update(UUID adminUserId, UUID id, NewsRequest r) {
        News n = news.findById(id).orElseThrow(NewsService::notFound);
        apply(n, r);
        audit.record(adminUserId, "ACTUALITE_MODIFIEE", "NEWS", id, n.getTitle());
        return NewsResponse.from(n);
    }

    private void apply(News n, NewsRequest r) {
        String cc = blankToNull(r.countryCode());
        if (cc != null) {
            cc = cc.toUpperCase(Locale.ROOT);
            if (!countries.existsById(cc)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PAYS_INCONNU", "Pays inconnu.");
            }
        }
        n.setTitle(r.title().trim());
        n.setBody(blankToNull(r.body()));
        n.setImageUrl(blankToNull(r.imageUrl()));
        n.setLinkUrl(blankToNull(r.linkUrl()));
        n.setVideoUrl(blankToNull(r.videoUrl()));
        n.setCountryCode(cc);
        n.setAudience(r.audience() == null ? NewsAudience.ALL : r.audience());
    }

    @Transactional
    public void delete(UUID adminUserId, UUID id) {
        News n = news.findById(id).orElseThrow(NewsService::notFound);
        news.delete(n);
        audit.record(adminUserId, "ACTUALITE_SUPPRIMEE", "NEWS", id, n.getTitle());
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ACTUALITE_INTROUVABLE", "Actualité introuvable.");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
