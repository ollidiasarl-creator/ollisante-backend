package com.ollisante.backend.service;

import com.ollisante.backend.domain.Role;
import com.ollisante.backend.domain.User;
import com.ollisante.backend.repository.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Crée le premier administrateur si BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD sont définis. */
@Component
@RequiredArgsConstructor
@Slf4j
public class BootstrapAdminRunner implements ApplicationRunner {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    @Value("${ollisante.bootstrap-admin.email:}")
    private String email;

    @Value("${ollisante.bootstrap-admin.password:}")
    private String password;

    @Override
    public void run(ApplicationArguments args) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(normalized)) {
            return;
        }
        if (password.length() < 12) {
            log.warn("BOOTSTRAP_ADMIN_PASSWORD trop court (12 caractères minimum) : administrateur non créé.");
            return;
        }
        User admin = new User();
        admin.setEmail(normalized);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole(Role.ADMIN);
        users.save(admin);
        log.warn("Compte administrateur créé pour {}. Retirez BOOTSTRAP_ADMIN_* de la configuration.", normalized);
    }
}
