package com.ollisante.backend.web;

import com.ollisante.backend.common.ApiException;
import com.ollisante.backend.domain.User;
import com.ollisante.backend.dto.AdminLoginDtos;
import com.ollisante.backend.dto.AuthResponse;
import com.ollisante.backend.dto.EmailSignupVerify;
import com.ollisante.backend.dto.LoginRequest;
import com.ollisante.backend.dto.OtpRequestResponse;
import com.ollisante.backend.dto.RefreshRequest;
import com.ollisante.backend.dto.RegisterPatientRequest;
import com.ollisante.backend.dto.RegisterPractitionerRequest;
import com.ollisante.backend.dto.StructureDtos;
import com.ollisante.backend.service.AdminTwoStepService;
import com.ollisante.backend.service.AuthService;
import com.ollisante.backend.service.EmailSignupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailSignupService emailSignups;
    private final AdminTwoStepService twoStep;

    /** Anciennes versions de l'application : l'inscription par e-mail passe désormais par un code de confirmation. */
    @PostMapping("/register/patient")
    public AuthResponse registerPatientLegacy(@RequestBody(required = false) Object ignored) {
        throw new ApiException(HttpStatus.GONE, "MISE_A_JOUR_REQUISE",
                "Mettez à jour l'application Ollisanté pour créer votre compte.");
    }

    /** Étape 1 : envoie un code à l'adresse e-mail. Le compte n'existe pas encore. */
    @PostMapping("/register/patient/start")
    public OtpRequestResponse startPatientSignup(@Valid @RequestBody RegisterPatientRequest request) {
        return new OtpRequestResponse(emailSignups.start(request));
    }

    /** Étape 2 : code correct → compte créé et connecté. */
    @PostMapping("/register/patient/verify")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse verifyPatientSignup(@Valid @RequestBody EmailSignupVerify request) {
        return emailSignups.verify(request);
    }

    @PostMapping("/register/practitioner")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse registerPractitioner(@Valid @RequestBody RegisterPractitionerRequest request) {
        return authService.registerPractitioner(request);
    }

    @PostMapping("/register/structure")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse registerStructure(@Valid @RequestBody StructureDtos.RegisterStructureRequest request) {
        return authService.registerStructure(request);
    }

    /**
     * 200 : session ouverte. 202 (administrateurs) : mot de passe correct, un code a été envoyé par e-mail ;
     * le saisir avec /login/verify.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        User user = authService.authenticate(request);
        if (twoStep.required(user, request.deviceToken())) {
            return ResponseEntity.accepted().body(twoStep.challenge(user));
        }
        return ResponseEntity.ok(authService.tokensFor(user));
    }

    @PostMapping("/login/verify")
    public AdminLoginDtos.Verified verifyLogin(@Valid @RequestBody AdminLoginDtos.Verify request) {
        return twoStep.verify(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
    }
}
