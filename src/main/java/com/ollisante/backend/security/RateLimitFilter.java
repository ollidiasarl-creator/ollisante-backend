package com.ollisante.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Garde-fou contre les rafales : nombre maximal de requêtes par minute et par adresse IP. Les limites sont
 * volontairement larges, car en Afrique beaucoup d'abonnés mobiles partagent la même adresse IP (CGNAT) :
 * elles arrêtent un robot ou une boucle, pas une salle d'attente pleine. Les codes SMS et e-mail ont en plus
 * leurs propres limites par numéro ou adresse.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final boolean enabled;
    private final int authPerMinute;
    private final int apiPerMinute;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(@Value("${ollisante.rate-limit.enabled:true}") boolean enabled,
                           @Value("${ollisante.rate-limit.auth-per-minute:60}") int authPerMinute,
                           @Value("${ollisante.rate-limit.api-per-minute:1200}") int apiPerMinute) {
        this.enabled = enabled;
        this.authPerMinute = authPerMinute;
        this.apiPerMinute = apiPerMinute;
    }

    private record Window(long minute, AtomicInteger count) {
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Rappels PawaPay et sonde de santé : jamais limités.
        return !enabled || !path.startsWith("/api/") || path.equals("/api/v1/payments/pawapay/callback");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean auth = request.getRequestURI().startsWith("/api/v1/auth/") && "POST".equals(request.getMethod());
        String ip = clientIp(request);
        int limit = auth ? authPerMinute : apiPerMinute;
        if (count((auth ? "auth:" : "api:") + ip) > limit) {
            log.warn("Limite atteinte ({} req/min) pour {} sur {}", limit, ip, request.getRequestURI());
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"status\":429,\"code\":\"TROP_DE_DEMANDES\","
                    + "\"message\":\"Trop de demandes en même temps. Réessayez dans une minute.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private int count(String key) {
        long minute = System.currentTimeMillis() / 60_000;
        Window w = windows.compute(key, (k, old) ->
                old != null && old.minute() == minute ? old : new Window(minute, new AtomicInteger()));
        return w.count().incrementAndGet();
    }

    /**
     * Derrière Apache (sur la même machine), l'adresse réelle est la dernière de X-Forwarded-For : Apache
     * l'ajoute en fin de liste, ce que le client ne peut pas falsifier.
     */
    static String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        String forwarded = request.getHeader("X-Forwarded-For");
        boolean local = "127.0.0.1".equals(remote) || "0:0:0:0:0:0:0:1".equals(remote) || "::1".equals(remote);
        if (local && forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            return parts[parts.length - 1].trim();
        }
        return remote;
    }

    /** Oublie les fenêtres terminées pour garder une mémoire stable. */
    @Scheduled(fixedDelay = 120_000)
    void purge() {
        long minute = System.currentTimeMillis() / 60_000;
        windows.values().removeIf(w -> w.minute() < minute);
    }
}
