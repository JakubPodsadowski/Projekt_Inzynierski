package com.podsadowski.warehouse.api.controller;

import com.podsadowski.warehouse.api.dto.InventoryBinResponse;
import com.podsadowski.warehouse.api.service.InventoryReadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryController.class)
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventoryReadService inventoryReadService;

    @Test
    void getInventory_returnsBins() throws Exception {
        when(inventoryReadService.findInventory(isNull(), isNull(), isNull())).thenReturn(List.of(
                new InventoryBinResponse(
                        1L, "A-01-01", "A", "01", "01",
                        10L, "SKU-SHOES", "Buty", 3, 6, 50.0, false
                )
        ));

        mockMvc.perform(get("/api/v1/inventory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].locationCode").value("A-01-01"))
                .andExpect(jsonPath("$[0].fillPercent").value(50.0))
                .andExpect(jsonPath("$[0].unitsPerBin").value(6));
    }

    @Test
    void getInventory_passesFilters() throws Exception {
        when(inventoryReadService.findInventory(eq("sku-1"), eq("a-01"), eq(true)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/inventory")
                        .param("sku", "sku-1")
                        .param("locationCode", "a-01")
                        .param("occupiedOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
