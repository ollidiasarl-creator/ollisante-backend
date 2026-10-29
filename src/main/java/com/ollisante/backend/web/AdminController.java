package com.ollisante.backend.web;

import com.ollisante.backend.domain.VerificationStatus;
import com.ollisante.backend.dto.AdminPractitionerCreate;
import com.ollisante.backend.dto.AdminPractitionerCreated;
import com.ollisante.backend.dto.AdminPractitionerResponse;
import com.ollisante.backend.dto.PageResponse;
import com.ollisante.backend.dto.VerificationRequest;
import com.ollisante.backend.security.CurrentUser;
import com.ollisante.backend.service.AdminService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Réservé au rôle ADMIN (règle dans SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/practitioners")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService service;

    @GetMapping
    public PageResponse<AdminPractitionerResponse> list(
            @RequestParam(defaultValue = "PENDING") VerificationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.listByStatus(status, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminPractitionerCreated create(Authentication authentication,
                                           @Valid @RequestBody AdminPractitionerCreate request) {
        AdminPractitionerResponse created = service.create(CurrentUser.id(authentication), request);
        boolean welcome = request.password() == null && service.sendWelcome(created, request.locale());
        return new AdminPractitionerCreated(created, welcome);
    }

    @PatchMapping("/{id}/verification")
    public AdminPractitionerResponse verify(Authentication authentication, @PathVariable UUID id,
                                            @Valid @RequestBody VerificationRequest request) {
        return service.setVerification(CurrentUser.id(authentication), id, request.status());
    }
}
