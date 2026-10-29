package com.ollisante.backend.web;

import com.ollisante.backend.dto.AuthResponse;
import com.ollisante.backend.dto.OtpRequestResponse;
import com.ollisante.backend.dto.PasswordForgotRequest;
import com.ollisante.backend.dto.PasswordResetRequest;
import com.ollisante.backend.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Mot de passe oublié (public, comme le reste de /api/v1/auth). */
@RestController
@RequestMapping("/api/v1/auth/password")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService service;

    @PostMapping("/forgot")
    public OtpRequestResponse forgot(@Valid @RequestBody PasswordForgotRequest request) {
        return new OtpRequestResponse(service.request(request.email(), request.locale()));
    }

    @PostMapping("/reset")
    public AuthResponse reset(@Valid @RequestBody PasswordResetRequest request) {
        return service.reset(request);
    }
}
