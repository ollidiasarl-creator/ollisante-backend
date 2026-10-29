package com.ollisante.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PendingPaymentExpiryJob {

    private final AppointmentService appointmentService;

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    public void run() {
        int expired = appointmentService.expirePendingPayments();
        if (expired > 0) {
            log.info("{} rendez-vous non payés annulés", expired);
        }
    }
}
