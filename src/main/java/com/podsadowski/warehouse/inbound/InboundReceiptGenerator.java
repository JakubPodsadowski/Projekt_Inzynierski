package com.podsadowski.warehouse.inbound;

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
 * Demo scheduler: pulls inbound shipments from the external WMS port into putaway queue.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InboundReceiptGenerator {

    private final ExternalWarehouseIngestionService ingestionService;
    private final MockExternalWarehouseAdapter mockExternalWarehouseAdapter;
    private final InboundPutawayService inboundPutawayService;
    private final DataSourceModeHolder modeHolder;
    private final SimulationRuntimeState simulationRuntimeState;
    private final WarehouseProperties warehouseProperties;

    @Scheduled(fixedDelayString = "${warehouse.simulation.inbound-generate-interval-ms:6000}")
    public void generateTick() {
        if (!modeHolder.isDemo() || simulationRuntimeState.isPaused()) {
            return;
        }
        int maxOpen = warehouseProperties.getSimulation().getMaxOpenPutawayReceipts();
        if (inboundPutawayService.countReadyToPutAway() >= maxOpen) {
            return;
        }
        try {
            if (mockExternalWarehouseAdapter.pendingInboundCount() == 0) {
                mockExternalWarehouseAdapter.enqueueSyntheticInbound(1);
            }
            int created = ingestionService.ingestPendingInbound(1);
            if (created > 0) {
                log.debug("Ingested {} inbound shipment(s) from WMS", created);
            }
        } catch (Exception e) {
            log.warn("Inbound shipment ingestion skipped: {}", e.getMessage());
        }
    }

    public int createRandomReceipts(int count) {
        return ingestionService.seedInbound(count);
    }
}
