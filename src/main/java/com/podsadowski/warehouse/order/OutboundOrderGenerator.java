package com.podsadowski.warehouse.order;

import com.podsadowski.warehouse.config.DataSourceModeHolder;
import com.podsadowski.warehouse.config.WarehouseProperties;
import com.podsadowski.warehouse.simulation.SimulationRuntimeState;
import com.podsadowski.warehouse.wms.ExternalWarehouseIngestionService;
import com.podsadowski.warehouse.wms.MockExternalWarehouseAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Demo scheduler: pulls outbound demand from the external WMS port into local pick queue.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboundOrderGenerator {

    private final ExternalWarehouseIngestionService ingestionService;
    private final MockExternalWarehouseAdapter mockExternalWarehouseAdapter;
    private final OutboundOrderService outboundOrderService;
    private final DataSourceModeHolder modeHolder;
    private final SimulationRuntimeState simulationRuntimeState;
    private final WarehouseProperties warehouseProperties;

    @Scheduled(fixedDelayString = "${warehouse.simulation.order-generate-interval-ms:4000}")
    public void generateTick() {
        if (!modeHolder.isDemo() || simulationRuntimeState.isPaused()) {
            return;
        }
        int maxOpen = warehouseProperties.getSimulation().getMaxOpenPickOrders();
        if (outboundOrderService.countReadyToPick() >= maxOpen) {
            return;
        }
        try {
            if (mockExternalWarehouseAdapter.pendingDemandCount() == 0) {
                mockExternalWarehouseAdapter.enqueueSyntheticDemand(1);
            }
            int created = ingestionService.ingestPendingDemand(1);
            if (created > 0) {
                log.debug("Ingested {} outbound demand message(s) from WMS", created);
            }
        } catch (Exception e) {
            log.warn("Outbound demand ingestion skipped: {}", e.getMessage());
        }
    }

    /** Seed / bootstrap helper. */
    public int createRandomOrders(int count) {
        return ingestionService.seedDemand(count);
    }

    /** Chaos helper via external WMS mock. */
    public SurgeResult surgeItems(int targetItems) {
        MockExternalWarehouseAdapter.SurgeResult result = ingestionService.surgeDemand(targetItems);
        return new SurgeResult(result.itemsCreated(), result.ordersCreated());
    }

    public record SurgeResult(int itemsCreated, int ordersCreated) {
    }
}
