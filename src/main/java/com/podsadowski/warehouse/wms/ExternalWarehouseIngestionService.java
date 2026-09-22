package com.podsadowski.warehouse.wms;

import com.podsadowski.warehouse.api.exception.InvalidRequestException;
import com.podsadowski.warehouse.api.exception.ResourceNotFoundException;
import com.podsadowski.warehouse.domain.entity.InboundReceipt;
import com.podsadowski.warehouse.domain.entity.OutboundOrder;
import com.podsadowski.warehouse.domain.entity.Product;
import com.podsadowski.warehouse.domain.entity.StorageLocation;
import com.podsadowski.warehouse.domain.repository.ProductRepository;
import com.podsadowski.warehouse.domain.repository.StorageLocationRepository;
import com.podsadowski.warehouse.inbound.InboundPutawayService;
import com.podsadowski.warehouse.order.OutboundOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Translates external WMS/OMS messages into local outbound orders and inbound receipts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalWarehouseIngestionService {

    private final ExternalWarehousePort externalWarehousePort;
    private final MockExternalWarehouseAdapter mockExternalWarehouseAdapter;
    private final OutboundOrderService outboundOrderService;
    private final InboundPutawayService inboundPutawayService;
    private final ProductRepository productRepository;
    private final StorageLocationRepository storageLocationRepository;

    @Transactional
    public int ingestPendingDemand(int max) {
        List<ExternalDemandMessage> messages = externalWarehousePort.pollDemand(max);
        int created = 0;
        for (ExternalDemandMessage message : messages) {
            try {
                ingestDemand(message);
                created++;
            } catch (Exception e) {
                log.warn("Failed to ingest demand {}: {}", message.externalRef(), e.getMessage());
            }
        }
        return created;
    }

    @Transactional
    public int ingestPendingInbound(int max) {
        List<ExternalInboundMessage> messages = externalWarehousePort.pollInbound(max);
        int created = 0;
        for (ExternalInboundMessage message : messages) {
            try {
                ingestInbound(message);
                created++;
            } catch (Exception e) {
                log.warn("Failed to ingest inbound {}: {}", message.externalRef(), e.getMessage());
            }
        }
        return created;
    }

    @Transactional
    public OutboundOrder ingestDemand(ExternalDemandMessage message) {
        if (message.lines() == null || message.lines().isEmpty()) {
            throw new InvalidRequestException("Demand must contain at least one line");
        }
        List<OutboundOrderService.OrderLineRequest> lines = new ArrayList<>();
        for (ExternalDemandMessage.ExternalLine line : message.lines()) {
            Product product = productRepository.findBySku(line.sku())
                    .orElseThrow(() -> new ResourceNotFoundException("Unknown SKU: " + line.sku()));
            lines.add(new OutboundOrderService.OrderLineRequest(product.getId(), line.quantity()));
        }
        return outboundOrderService.createOrder(lines, message.dueAt(), message.vip(), message.externalRef());
    }

    @Transactional
    public InboundReceipt ingestInbound(ExternalInboundMessage message) {
        if (message.lines() == null || message.lines().isEmpty()) {
            throw new InvalidRequestException("Inbound shipment must contain at least one line");
        }
        List<InboundPutawayService.ReceiptLineRequest> lines = new ArrayList<>();
        for (ExternalInboundMessage.ExternalLine line : message.lines()) {
            Product product = productRepository.findBySku(line.sku())
                    .orElseThrow(() -> new ResourceNotFoundException("Unknown SKU: " + line.sku()));
            Long locationId = null;
            if (line.preferredLocationCode() != null && !line.preferredLocationCode().isBlank()) {
                locationId = storageLocationRepository.findByCode(line.preferredLocationCode())
                        .map(StorageLocation::getId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Unknown location: " + line.preferredLocationCode()));
            }
            lines.add(new InboundPutawayService.ReceiptLineRequest(product.getId(), line.quantity(), locationId));
        }
        return inboundPutawayService.createReceipt(lines, message.externalRef());
    }

    /** Demo/seed: synthesize via mock, then ingest into domain. */
    public int seedDemand(int count) {
        int queued = mockExternalWarehouseAdapter.enqueueSyntheticDemand(count);
        return ingestPendingDemand(queued);
    }

    public int seedInbound(int count) {
        int queued = mockExternalWarehouseAdapter.enqueueSyntheticInbound(count);
        return ingestPendingInbound(queued);
    }

    public MockExternalWarehouseAdapter.SurgeResult surgeDemand(int targetItems) {
        MockExternalWarehouseAdapter.SurgeResult queued = mockExternalWarehouseAdapter.enqueueDemandSurge(targetItems);
        int ingested = ingestPendingDemand(queued.ordersCreated() + 5);
        return new MockExternalWarehouseAdapter.SurgeResult(queued.itemsCreated(), ingested);
    }

    public void submitDemand(ExternalDemandMessage message) {
        externalWarehousePort.submitDemand(message);
    }

    public void submitInbound(ExternalInboundMessage message) {
        externalWarehousePort.submitInbound(message);
    }
}
