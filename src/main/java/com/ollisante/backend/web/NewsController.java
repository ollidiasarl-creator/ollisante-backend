package com.ollisante.backend.web;

import com.ollisante.backend.dto.NewsRequest;
import com.ollisante.backend.dto.NewsResponse;
import com.ollisante.backend.dto.PageResponse;
import com.ollisante.backend.security.CurrentUser;
import com.ollisante.backend.service.NewsService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import com.ollisante.backend.service.CountryScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class NewsController {

    private final NewsService service;
    private final CountryScope scope;

    /** Public : un visiteur non connecté voit les actualités destinées aux patients (écran d'ouverture). */
    @GetMapping("/news")
    public PageResponse<NewsResponse> list(Authentication authentication,
                                           @RequestParam(required = false) String countryCode,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        // Compte connecté (hors administrateur) : les actualités de son pays et celles de toute l'Afrique.
        String country = scope.of(authentication).orElse(countryCode);
        return service.list(CurrentUser.roleOrNull(authentication), country, page, size);
    }

    /** Réservé au rôle ADMIN (règle /api/v1/admin/** dans SecurityConfig). */
    @PostMapping("/admin/news")
    @ResponseStatus(HttpStatus.CREATED)
    public NewsResponse create(Authentication authentication, @Valid @RequestBody NewsRequest request) {
        return service.create(CurrentUser.id(authentication), request);
    }

    @PatchMapping("/admin/news/{id}")
    public NewsResponse update(Authentication authentication, @PathVariable UUID id,
                               @Valid @RequestBody NewsRequest request) {
        return service.update(CurrentUser.id(authentication), id, request);
    }

    @DeleteMapping("/admin/news/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication, @PathVariable UUID id) {
        service.delete(CurrentUser.id(authentication), id);
    }
}
