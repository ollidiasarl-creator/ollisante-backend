package com.ollisante.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ollisante.booking")
/**
 * maxPendingPerPatient / maxUpcomingPerPatient : limites anti-blocage d'agenda (un compte ne peut pas
 * réserver en masse pour empêcher les vrais patients de prendre rendez-vous).
 */
public record BookingProperties(int minNoticeMinutes, int maxDaysAhead, int pendingPaymentMinutes,
                                int maxPendingPerPatient, int maxUpcomingPerPatient) {
}
