package com.ollisante.backend.config;

import com.ollisante.backend.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter,
                                                    @Qualifier("corsConfigurationSource")
                                                    CorsConfigurationSource corsSource) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsSource))
                .csrf(csrf -> csrf.disable()) // API stateless authentifiée par jeton, sans cookie de session
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // HTTPS imposé aux navigateurs (le serveur est derrière Apache, d'où le « toute requête »),
                // et pages d'administration protégées contre l'insertion de contenus étrangers.
                .headers(h -> h
                        .httpStrictTransportSecurity(hsts -> hsts.requestMatcher(AnyRequestMatcher.INSTANCE)
                                .includeSubDomains(false).maxAgeInSeconds(31_536_000))
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; img-src 'self' data: https:;"
                                + " style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com;"
                                + " frame-ancestors 'none'; base-uri 'self'; form-action 'self'"))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/api/v1/countries",
                                "/api/v1/specialties",
                                "/api/v1/faq",
                                "/api/v1/app-config",
                                "/api/v1/payments/pawapay/callback",
                                "/api/v1/campaign/events",
                                "/api/v1/reports",
                                "/actuator/health",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html").permitAll()
                        // Consultation publique : recherche de praticiens, créneaux, établissements
                        .requestMatchers(HttpMethod.GET, "/api/v1/practitioners/**", "/api/v1/facilities", "/api/v1/facilities/**",
                                "/api/v1/news", "/media/**").permitAll()
                        // Pages du site d'administration (publiques) ; ses données passent par /api/v1/admin/**
                        .requestMatchers(HttpMethod.GET, "/admin", "/admin/", "/admin/**", "/structure", "/structure/").permitAll()
                        // Conditions générales, confidentialité, mentions légales (pages publiques)
                        .requestMatchers(HttpMethod.GET, "/legal/**").permitAll()
                        // Site public des patients
                        .requestMatchers(HttpMethod.GET, "/", "/sw.js", "/site", "/site/", "/site/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                writeJson(res, 401, "NON_AUTHENTIFIE", "Authentification requise."))
                        .accessDeniedHandler((req, res, ex) ->
                                writeJson(res, 403, "ACCES_REFUSE", "Accès refusé.")))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Origines web autorisées à appeler l'API depuis un navigateur (version web de l'application).
     * Liste vide par défaut : aucune. Les applications mobiles natives ne sont pas concernées par CORS.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${ollisante.cors.allowed-origins:}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins.stream().map(String::trim).filter(o -> !o.isEmpty()).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    private static void writeJson(HttpServletResponse res, int status, String code, String message)
            throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"status\":" + status + ",\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
