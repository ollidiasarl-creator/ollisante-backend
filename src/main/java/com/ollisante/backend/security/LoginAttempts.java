package com.ollisante.backend.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Contre la recherche de mot de passe par essais successifs : après N échecs sur un même identifiant,
 * celui-ci est bloqué quelques minutes, quelle que soit l'adresse IP de l'attaquant. Un compte inexistant
 * est traité de la même façon, pour ne pas révéler qui est inscrit.
 */
@Component
public class LoginAttempts {

    private final int maxFailures;
    private final Duration lockDuration;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public LoginAttempts(@Value("${ollisante.login.max-failures:5}") int maxFailures,
                         @Value("${ollisante.login.lock-minutes:15}") int lockMinutes) {
        this.maxFailures = maxFailures;
        this.lockDuration = Duration.ofMinutes(lockMinutes);
    }

    private record Entry(int failures, Instant since) {
    }

    public boolean isLocked(String identifier) {
        Entry e = entries.get(identifier);
        return e != null && e.failures() >= maxFailures && e.since().plus(lockDuration).isAfter(Instant.now());
    }

    public void failed(String identifier) {
        Instant now = Instant.now();
        entries.compute(identifier, (k, old) -> old == null || old.since().plus(lockDuration).isBefore(now)
                ? new Entry(1, now) : new Entry(old.failures() + 1, old.since()));
    }

    public void succeeded(String identifier) {
        entries.remove(identifier);
    }

    @Scheduled(fixedDelay = 300_000)
    void purge() {
        Instant limit = Instant.now().minus(lockDuration);
        entries.values().removeIf(e -> e.since().isBefore(limit));
    }
}
