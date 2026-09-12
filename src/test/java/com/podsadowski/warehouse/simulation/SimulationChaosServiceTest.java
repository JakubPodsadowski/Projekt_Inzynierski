package com.podsadowski.warehouse.simulation;

import com.podsadowski.warehouse.order.OutboundOrderGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimulationChaosServiceTest {

    @Mock
    private OutboundOrderGenerator outboundOrderGenerator;

    private SimulationChaosService chaosService;

    @BeforeEach
    void setUp() {
        chaosService = new SimulationChaosService(outboundOrderGenerator);
    }

    @Test
    void surgeOrders_delegatesToOutboundGenerator() {
        when(outboundOrderGenerator.surgeItems(500))
                .thenReturn(new OutboundOrderGenerator.SurgeResult(48, 12));

        SimulationChaosService.SurgeResult result = chaosService.surgeOrders(500);

        assertThat(result.itemsCreated()).isEqualTo(48);
        assertThat(result.ordersCreated()).isEqualTo(12);
        verify(outboundOrderGenerator).surgeItems(500);
    }
}
