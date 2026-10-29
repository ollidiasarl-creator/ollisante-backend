package com.ollisante.backend.web;

import com.ollisante.backend.dto.AuthResponse;
import com.ollisante.backend.dto.OtpRequest;
import com.ollisante.backend.dto.OtpRequestResponse;
import com.ollisante.backend.dto.OtpVerifyRequest;
import com.ollisante.backend.service.OtpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/otp")
@RequiredArgsConstructor
public class OtpController {

    private final OtpService service;

    @PostMapping("/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OtpRequestResponse request(@Valid @RequestBody OtpRequest request) {
        return new OtpRequestResponse(service.request(request.phone(), request.locale()));
    }

    @PostMapping("/verify")
    public AuthResponse verify(@Valid @RequestBody OtpVerifyRequest request) {
        return service.verify(request);
    }
}
