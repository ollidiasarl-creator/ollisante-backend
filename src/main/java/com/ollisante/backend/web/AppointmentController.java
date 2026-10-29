package com.ollisante.backend.web;

import com.ollisante.backend.dto.AppointmentResponse;
import com.ollisante.backend.dto.BookAppointmentRequest;
import com.ollisante.backend.dto.CancelAppointmentRequest;
import com.ollisante.backend.payment.PaymentService;
import com.ollisante.backend.security.CurrentUser;
import com.ollisante.backend.service.AppointmentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService service;

    @PostMapping("/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PATIENT')")
    public AppointmentResponse book(Authentication authentication,
                                    @Valid @RequestBody BookAppointmentRequest request) {
        return service.book(CurrentUser.id(authentication), request);
    }

    @GetMapping("/me/appointments")
    @PreAuthorize("hasAnyRole('PATIENT','PRACTITIONER')")
    public List<AppointmentResponse> mine(Authentication authentication) {
        return service.mine(CurrentUser.id(authentication), CurrentUser.role(authentication));
    }

    @PostMapping("/appointments/{id}/cancel")
    @PreAuthorize("hasAnyRole('PATIENT','PRACTITIONER','ADMIN','STRUCTURE')")
    public AppointmentResponse cancel(Authentication authentication, @PathVariable UUID id,
                                      @Valid @RequestBody(required = false) CancelAppointmentRequest request) {
        String reason = request == null ? null : request.reason();
        return service.cancel(CurrentUser.id(authentication), CurrentUser.role(authentication), id, reason);
    }

    @GetMapping("/appointments/{id}/cancellation")
    @PreAuthorize("hasAnyRole('PATIENT','PRACTITIONER','ADMIN','STRUCTURE')")
    public PaymentService.CancellationPreview cancellationPreview(Authentication authentication, @PathVariable UUID id) {
        return service.cancellationPreview(CurrentUser.id(authentication), CurrentUser.role(authentication), id);
    }
}
