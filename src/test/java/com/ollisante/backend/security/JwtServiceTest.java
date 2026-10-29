package com.ollisante.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ollisante.backend.config.JwtProperties;
import com.ollisante.backend.domain.Role;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "0123456789012345678901234567890123456789";

    private JwtService service() {
        return new JwtService(new JwtProperties(SECRET, 15, 30));
    }

    @Test
    void generatedTokenCanBeParsed() {
        UUID id = UUID.randomUUID();
        String token = service().generateAccessToken(id, Role.PATIENT);

        TokenData data = service().parse(token).orElseThrow();

        assertEquals(id, data.userId());
        assertEquals(Role.PATIENT, data.role());
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = service().generateAccessToken(UUID.randomUUID(), Role.ADMIN);

        assertTrue(service().parse(token + "x").isEmpty());
        assertTrue(service().parse("not-a-jwt").isEmpty());
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        JwtService other = new JwtService(new JwtProperties("9876543210987654321098765432109876543210", 15, 30));
        String token = other.generateAccessToken(UUID.randomUUID(), Role.PATIENT);

        assertTrue(service().parse(token).isEmpty());
    }

    @Test
    void shortSecretIsRejectedAtStartup() {
        assertThrows(IllegalStateException.class, () -> new JwtService(new JwtProperties("short", 15, 30)));
    }
}
