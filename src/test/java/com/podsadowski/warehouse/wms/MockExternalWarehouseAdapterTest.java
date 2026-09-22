package com.podsadowski.warehouse.wms;

import com.podsadowski.warehouse.domain.repository.ProductRepository;
import com.podsadowski.warehouse.domain.repository.StockLevelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class MockExternalWarehouseAdapterTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private StockLevelRepository stockLevelRepository;

    private MockExternalWarehouseAdapter adapter;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneId.of("UTC"));
        adapter = new MockExternalWarehouseAdapter(productRepository, stockLevelRepository, clock);
    }

    @Test
    void submitAndPollDemand_isFifo() {
        ExternalDemandMessage first = new ExternalDemandMessage(
                "A", false, LocalDateTime.now(), List.of(new ExternalDemandMessage.ExternalLine("SKU-1", 1)));
        ExternalDemandMessage second = new ExternalDemandMessage(
                "B", true, LocalDateTime.now(), List.of(new ExternalDemandMessage.ExternalLine("SKU-2", 2)));

        adapter.submitDemand(first);
        adapter.submitDemand(second);

        assertThat(adapter.pendingDemandCount()).isEqualTo(2);
        assertThat(adapter.pollDemand(1)).extracting(ExternalDemandMessage::externalRef).containsExactly("A");
        assertThat(adapter.pollDemand(5)).extracting(ExternalDemandMessage::externalRef).containsExactly("B");
        assertThat(adapter.pendingDemandCount()).isZero();
    }

    @Test
    void submitAndPollInbound_works() {
        adapter.submitInbound(new ExternalInboundMessage(
                "IN-1", List.of(new ExternalInboundMessage.ExternalLine("SKU-1", 5))));

        assertThat(adapter.pollInbound(1)).hasSize(1);
        assertThat(adapter.pendingInboundCount()).isZero();
    }
}
