package com.ollisante.backend.web;

import com.ollisante.backend.dto.AdminDtos.Settings;
import com.ollisante.backend.security.CurrentUser;
import com.ollisante.backend.service.SettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService service;

    /** Public : réglages utiles à l'application et au site (vide = bouton masqué). */
    @GetMapping("/app-config")
    public Settings appConfig() {
        return service.get();
    }

    @GetMapping("/admin/settings")
    public Settings settings() {
        return service.get();
    }

    @PutMapping("/admin/settings")
    public Settings update(Authentication auth, @Valid @RequestBody Settings r) {
        return service.update(CurrentUser.id(auth), r);
    }
}
