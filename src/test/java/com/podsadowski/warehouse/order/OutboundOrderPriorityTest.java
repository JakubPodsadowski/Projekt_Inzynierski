package com.podsadowski.warehouse.order;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class OutboundOrderPriorityTest {

    @Test
    void vipAndSoonDueBeatNormalAgedOrder() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 12, 12, 0);
        LocalDateTime created = now.minusMinutes(10);

        int normal = OutboundOrderService.computePriorityScore(false, now.plusHours(3), created, now);
        int vipSoon = OutboundOrderService.computePriorityScore(true, now.plusMinutes(20), created, now);

        assertThat(vipSoon).isGreaterThan(normal);
    }

    @Test
    void olderOrdersGainPriority() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 12, 12, 0);
        int fresh = OutboundOrderService.computePriorityScore(false, null, now.minusMinutes(5), now);
        int aged = OutboundOrderService.computePriorityScore(false, null, now.minusMinutes(120), now);

        assertThat(aged).isGreaterThan(fresh);
    }
}
