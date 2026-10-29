package com.ollisante.backend.web;

import com.ollisante.backend.security.CurrentUser;
import com.ollisante.backend.service.ReportService;
import com.ollisante.backend.service.ReportService.ReportRequest;
import com.ollisante.backend.service.ReportService.ReportSummary;
import com.ollisante.backend.service.VerificationService;
import com.ollisante.backend.service.VerificationService.DocumentInfo;
import com.ollisante.backend.service.VerificationService.Owner;
import com.ollisante.backend.service.VerificationService.Review;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Confiance : pièces justificatives des professionnels, signalements du public, et leur examen par l'équipe. */
@RestController
@RequiredArgsConstructor
public class TrustController {

    private final VerificationService verification;
    private final ReportService reports;

    // ----- le médecin ou la structure : ses pièces -----

    @GetMapping("/api/v1/me/verification-documents")
    public List<DocumentInfo> myDocuments(Authentication auth) {
        return verification.list(verification.ownerOf(CurrentUser.id(auth)));
    }

    @PostMapping("/api/v1/me/verification-documents")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentInfo upload(Authentication auth, @RequestParam("kind") String kind, @RequestParam("file") MultipartFile file) {
        return verification.store(verification.ownerOf(CurrentUser.id(auth)), kind, file);
    }

    @DeleteMapping("/api/v1/me/verification-documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocument(Authentication auth, @PathVariable UUID id) {
        verification.delete(verification.ownerOf(CurrentUser.id(auth)), id);
    }

    // ----- le public : signaler un profil (avec ou sans compte) -----

    @PostMapping("/api/v1/reports")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void report(Authentication auth, @RequestBody ReportRequest request, HttpServletRequest http) {
        UUID user = auth == null || auth instanceof AnonymousAuthenticationToken ? null : CurrentUser.id(auth);
        reports.report(request, user, clientIp(http));
    }

    // ----- l'équipe d'administration (règle /api/v1/admin/** : rôle ADMIN) -----

    /** {@code type} : practitioner ou organization. */
    @GetMapping("/api/v1/admin/verification/{type}/{id}")
    public Review review(@PathVariable String type, @PathVariable UUID id) {
        return verification.review(new Owner("organization".equals(type) ? "ORGANIZATION" : "PRACTITIONER", id));
    }

    @GetMapping("/api/v1/admin/verification-documents/{id}")
    public ResponseEntity<Resource> document(@PathVariable UUID id) {
        return verification.file(id)
                .filter(f -> Files.isRegularFile((Path) f[0]))
                .<ResponseEntity<Resource>>map(f -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore())
                        .contentType(MediaType.parseMediaType((String) f[1]))
                        .body(new FileSystemResource((Path) f[0])))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/api/v1/admin/reports")
    public List<ReportSummary> openReports() {
        return reports.open();
    }

    public record CloseRequest(boolean reinstate) {
    }

    @PostMapping("/api/v1/admin/reports/{targetType}/{targetId}/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void close(Authentication auth, @PathVariable String targetType, @PathVariable UUID targetId,
                      @RequestBody CloseRequest request) {
        reports.close(CurrentUser.id(auth), targetType, targetId, request.reinstate());
    }

    /** Derrière Apache, l'adresse réelle est la dernière de X-Forwarded-For (même règle que la limite de débit). */
    private static String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        String forwarded = request.getHeader("X-Forwarded-For");
        boolean local = "127.0.0.1".equals(remote) || "0:0:0:0:0:0:0:1".equals(remote) || "::1".equals(remote);
        if (local && forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            return parts[parts.length - 1].trim();
        }
        return remote;
    }
}
