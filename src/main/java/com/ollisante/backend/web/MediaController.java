package com.ollisante.backend.web;

import com.ollisante.backend.dto.MediaResponse;
import com.ollisante.backend.service.MediaService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class MediaController {

    private final MediaService service;

    /** Réservé au rôle ADMIN (règle /api/v1/admin/** dans SecurityConfig). */
    @PostMapping("/api/v1/admin/media")
    @ResponseStatus(HttpStatus.CREATED)
    public MediaResponse upload(@RequestParam("file") MultipartFile file) {
        return new MediaResponse(service.store(file));
    }

    /** Public : les noms sont aléatoires et les fichiers ne changent jamais. */
    @GetMapping("/media/{name}")
    public ResponseEntity<Resource> get(@PathVariable String name) {
        return service.find(name)
                .<ResponseEntity<Resource>>map(path -> ResponseEntity.ok()
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                        .contentType(MediaTypeFactory.getMediaType(name).orElse(MediaType.APPLICATION_OCTET_STREAM))
                        .body(new FileSystemResource(path)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
