package com.podsadowski.warehouse.api.controller;

import com.podsadowski.warehouse.api.dto.InjectDemandRequest;
import com.podsadowski.warehouse.api.dto.InjectDemandResponse;
import com.podsadowski.warehouse.api.dto.InjectInboundRequest;
import com.podsadowski.warehouse.api.dto.InjectInboundResponse;
import com.podsadowski.warehouse.api.service.ExternalWmsApiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST facade simulating an external WMS/OMS pushing demand and inbound shipments.
 */
@RestController
@RequestMapping("/api/v1/wms")
@RequiredArgsConstructor
public class ExternalWmsController {

    private final ExternalWmsApiService externalWmsApiService;

    @PostMapping("/demand")
    @ResponseStatus(HttpStatus.CREATED)
    public InjectDemandResponse injectDemand(@Valid @RequestBody InjectDemandRequest request) {
        return externalWmsApiService.injectDemand(request);
    }

    @PostMapping("/inbound")
    @ResponseStatus(HttpStatus.CREATED)
    public InjectInboundResponse injectInbound(@Valid @RequestBody InjectInboundRequest request) {
        return externalWmsApiService.injectInbound(request);
    }
}
