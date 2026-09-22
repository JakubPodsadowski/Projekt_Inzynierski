package com.podsadowski.warehouse.api.service;

import com.podsadowski.warehouse.api.dto.InjectDemandRequest;
import com.podsadowski.warehouse.api.dto.InjectDemandResponse;
import com.podsadowski.warehouse.api.dto.InjectInboundRequest;
import com.podsadowski.warehouse.api.dto.InjectInboundResponse;
import com.podsadowski.warehouse.domain.entity.InboundReceipt;
import com.podsadowski.warehouse.domain.entity.OutboundOrder;
import com.podsadowski.warehouse.wms.ExternalDemandMessage;
import com.podsadowski.warehouse.wms.ExternalInboundMessage;
import com.podsadowski.warehouse.wms.ExternalWarehouseIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExternalWmsApiService {

    private final ExternalWarehouseIngestionService ingestionService;

    public InjectDemandResponse injectDemand(InjectDemandRequest request) {
        String externalRef = request.externalRef() != null && !request.externalRef().isBlank()
                ? request.externalRef()
                : "API-DEMAND-" + UUID.randomUUID();
        List<ExternalDemandMessage.ExternalLine> lines = request.lines().stream()
                .map(line -> new ExternalDemandMessage.ExternalLine(line.sku(), line.quantity()))
                .toList();
        ExternalDemandMessage message = new ExternalDemandMessage(
                externalRef, request.vip(), request.dueAt(), lines);
        OutboundOrder order = ingestionService.ingestDemand(message);
        return new InjectDemandResponse(
                order.getId(), order.getOrderNumber(), order.getExternalRef(), order.totalOrdered());
    }

    public InjectInboundResponse injectInbound(InjectInboundRequest request) {
        String externalRef = request.externalRef() != null && !request.externalRef().isBlank()
                ? request.externalRef()
                : "API-IN-" + UUID.randomUUID();
        List<ExternalInboundMessage.ExternalLine> lines = request.lines().stream()
                .map(line -> new ExternalInboundMessage.ExternalLine(
                        line.sku(), line.quantity(), line.preferredLocationCode()))
                .toList();
        ExternalInboundMessage message = new ExternalInboundMessage(externalRef, lines);
        InboundReceipt receipt = ingestionService.ingestInbound(message);
        return new InjectInboundResponse(
                receipt.getId(), receipt.getReceiptNumber(), receipt.getExternalRef(), receipt.totalExpected());
    }
}
