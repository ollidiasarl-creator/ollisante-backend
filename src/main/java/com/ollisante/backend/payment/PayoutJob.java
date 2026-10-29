package com.ollisante.backend.payment;

import java.time.LocalDate;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Chaque vendredi matin : relevés des sommes dues aux praticiens (le virement reste manuel). */
@Component
@RequiredArgsConstructor
@Slf4j
public class PayoutJob {

    private final AccountingService accounting;

    @Scheduled(cron = "0 0 6 * * FRI", zone = "UTC")
    public void run() {
        int n = accounting.generatePayouts(null, AccountingService.lastFriday(LocalDate.now(ZoneOffset.UTC))).size();
        log.info("{} relevé(s) de reversement préparé(s)", n);
    }
}
