package com.ollisante.backend.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ollisante.backend.common.ApiException;
import com.ollisante.backend.domain.Role;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PaymentRulesTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");

    private static int patient(Duration before) {
        return PaymentService.refundPercent(Role.PATIENT, NOW.plus(before), NOW);
    }

    @Test
    void patientCancellationFollowsTheDelays() {
        assertEquals(100, patient(Duration.ofHours(48)));
        assertEquals(100, patient(Duration.ofHours(24)));
        assertEquals(50, patient(Duration.ofHours(24).minusMinutes(1)));
        assertEquals(50, patient(Duration.ofHours(6)));
        assertEquals(0, patient(Duration.ofHours(6).minusMinutes(1)));
        assertEquals(0, patient(Duration.ofMinutes(10)));
    }

    @Test
    void practitionerOrAdminCancellationIsAlwaysFullyRefunded() {
        Instant soon = NOW.plus(Duration.ofMinutes(30));
        assertEquals(100, PaymentService.refundPercent(Role.PRACTITIONER, soon, NOW));
        assertEquals(100, PaymentService.refundPercent(Role.ADMIN, soon, NOW));
    }

    @Test
    void amountsUseTheCurrencyUnit() {
        assertEquals("500", Money.toApi(500, "XOF"));
        assertEquals("50000", Money.toApi(50000, "GNF"));
        assertEquals("10", Money.toApi(1000, "USD"));
        assertEquals("5.5", Money.toApi(550, "USD"));
        assertEquals(250, Money.percent(500, 50));
        assertEquals(500, Money.percent(1000, 50));
        assertEquals(0, Money.percent(1, 50));
        assertEquals("SEN", Money.iso3("SN"));
        assertEquals("COD", Money.iso3("CD"));
    }

    @Test
    void phoneNumbersAreNormalisedForPawaPay() {
        assertEquals("221771234567", PaymentService.msisdn("77 123 45 67", "221"));
        assertEquals("221771234567", PaymentService.msisdn("+221 77-123-45-67", "221"));
        assertEquals("221771234567", PaymentService.msisdn("00221771234567", "221"));
        assertEquals("243971234567", PaymentService.msisdn("0971234567", "243"));
        assertThrows(ApiException.class, () -> PaymentService.msisdn("+33 6 12 34 56 78", "221"));
        assertThrows(ApiException.class, () -> PaymentService.msisdn("77 ABC", "221"));
        assertThrows(ApiException.class, () -> PaymentService.msisdn("123", "221"));
    }

    @Test
    void statementsAreDatedOnFriday() {
        assertEquals(LocalDate.parse("2026-10-16"), AccountingService.lastFriday(LocalDate.parse("2026-10-16")));
        assertEquals(LocalDate.parse("2026-10-16"), AccountingService.lastFriday(LocalDate.parse("2026-10-21")));
    }
}
