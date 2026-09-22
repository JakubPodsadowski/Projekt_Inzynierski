package com.podsadowski.warehouse.api.controller;

import com.podsadowski.warehouse.api.dto.InjectDemandRequest;
import com.podsadowski.warehouse.api.dto.InjectDemandResponse;
import com.podsadowski.warehouse.api.dto.InjectInboundRequest;
import com.podsadowski.warehouse.api.dto.InjectInboundResponse;
import com.podsadowski.warehouse.api.exception.GlobalExceptionHandler;
import com.podsadowski.warehouse.api.service.ExternalWmsApiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExternalWmsController.class)
@Import(GlobalExceptionHandler.class)
class ExternalWmsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExternalWmsApiService externalWmsApiService;

    @Test
    void injectDemand_returnsCreatedOrder() throws Exception {
        when(externalWmsApiService.injectDemand(any(InjectDemandRequest.class)))
                .thenReturn(new InjectDemandResponse(10L, "OUT-1", "EXT-1", 4));

        mockMvc.perform(post("/api/v1/wms/demand")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "externalRef": "EXT-1",
                                  "vip": true,
                                  "lines": [{"sku": "SKU-1001", "quantity": 4}]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNumber").value("OUT-1"))
                .andExpect(jsonPath("$.totalItems").value(4));
    }

    @Test
    void injectInbound_returnsCreatedReceipt() throws Exception {
        when(externalWmsApiService.injectInbound(any(InjectInboundRequest.class)))
                .thenReturn(new InjectInboundResponse(7L, "IN-1", "ASN-9", 20));

        mockMvc.perform(post("/api/v1/wms/inbound")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "externalRef": "ASN-9",
                                  "lines": [{"sku": "SKU-1002", "quantity": 20}]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receiptNumber").value("IN-1"))
                .andExpect(jsonPath("$.totalItems").value(20));
    }
}
