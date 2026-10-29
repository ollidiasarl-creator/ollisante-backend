package com.ollisante.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class OtpHashTest {

    private static final String SECRET = "0123456789012345678901234567890123456789";

    @Test
    void sameInputGivesSameHash() {
        assertEquals(OtpService.hash(SECRET, "+22370000000", "123456"),
                OtpService.hash(SECRET, "+22370000000", "123456"));
    }

    @Test
    void hashDependsOnCodePhoneAndSecret() {
        String base = OtpService.hash(SECRET, "+22370000000", "123456");
        assertNotEquals(base, OtpService.hash(SECRET, "+22370000000", "123457"));
        assertNotEquals(base, OtpService.hash(SECRET, "+22370000001", "123456"));
        assertNotEquals(base, OtpService.hash("9876543210987654321098765432109876543210", "+22370000000", "123456"));
    }

    @Test
    void hashIsHexOfSha256Size() {
        assertEquals(64, OtpService.hash(SECRET, "+22370000000", "123456").length());
    }
}
