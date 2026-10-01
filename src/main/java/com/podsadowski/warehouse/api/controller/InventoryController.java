package com.podsadowski.warehouse.api.controller;

import com.podsadowski.warehouse.api.dto.InventoryBinResponse;
import com.podsadowski.warehouse.api.service.InventoryReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryReadService inventoryReadService;

    @GetMapping
    public List<InventoryBinResponse> getInventory(
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String locationCode,
            @RequestParam(required = false) Boolean occupiedOnly
    ) {
        return inventoryReadService.findInventory(sku, locationCode, occupiedOnly);
    }
}
