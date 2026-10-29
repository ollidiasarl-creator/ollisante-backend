package com.ollisante.backend.web;

import com.ollisante.backend.dto.AvailabilityRuleRequest;
import com.ollisante.backend.dto.AvailabilityRuleResponse;
import com.ollisante.backend.security.CurrentUser;
import com.ollisante.backend.service.AvailabilityService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/availability-rules")
@PreAuthorize("hasRole('PRACTITIONER')")
@RequiredArgsConstructor
public class AvailabilityController {

    private final AvailabilityService service;

    @GetMapping
    public List<AvailabilityRuleResponse> list(Authentication authentication) {
        return service.list(CurrentUser.id(authentication));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AvailabilityRuleResponse create(Authentication authentication,
                                           @Valid @RequestBody AvailabilityRuleRequest request) {
        return service.create(CurrentUser.id(authentication), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication, @PathVariable UUID id) {
        service.delete(CurrentUser.id(authentication), id);
    }
}
