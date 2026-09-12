package com.podsadowski.warehouse.api.controller;

import com.podsadowski.warehouse.api.dto.OrderSurgeRequest;
import com.podsadowski.warehouse.api.dto.OrderSurgeResponse;
import com.podsadowski.warehouse.api.dto.SetSimulationSpeedRequest;
import com.podsadowski.warehouse.api.dto.SimulationStatusResponse;
import com.podsadowski.warehouse.api.exception.GlobalExceptionHandler;
import com.podsadowski.warehouse.api.exception.InvalidRequestException;
import com.podsadowski.warehouse.api.service.SimulationControlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SimulationController.class)
@Import(GlobalExceptionHandler.class)
class SimulationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SimulationControlService simulationControlService;

    @Test
    void getStatus_returnsState() throws Exception {
        when(simulationControlService.getStatus()).thenReturn(new SimulationStatusResponse(
                LocalDateTime.of(2026, 9, 4, 6, 15, 30),
                3,
                false,
                true,
                true
        ));

        mockMvc.perform(get("/api/v1/simulation/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.speed").value(3))
                .andExpect(jsonPath("$.paused").value(false))
                .andExpect(jsonPath("$.running").value(true))
                .andExpect(jsonPath("$.profileDemo").value(true));
    }

    @Test
    void setSpeed_returnsUpdated() throws Exception {
        when(simulationControlService.setSpeed(any(SetSimulationSpeedRequest.class)))
                .thenReturn(new SimulationStatusResponse(
                        LocalDateTime.of(2026, 9, 4, 6, 0),
                        5,
                        false,
                        true,
                        true
                ));

        mockMvc.perform(post("/api/v1/simulation/speed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"speed\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.speed").value(5));
    }

    @Test
    void pause_returnsPaused() throws Exception {
        when(simulationControlService.pause()).thenReturn(new SimulationStatusResponse(
                LocalDateTime.of(2026, 9, 4, 6, 0),
                1,
                true,
                false,
                true
        ));

        mockMvc.perform(post("/api/v1/simulation/pause"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paused").value(true));
    }

    @Test
    void orderSurge_returnsCounts() throws Exception {
        when(simulationControlService.orderSurge(any(OrderSurgeRequest.class)))
                .thenReturn(new OrderSurgeResponse(500, 38));

        mockMvc.perform(post("/api/v1/simulation/chaos/order-surge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemsPerEmployee\":500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemsPerEmployee").value(500))
                .andExpect(jsonPath("$.affectedEmployees").value(38));
    }

    @Test
    void setSpeed_withoutDemo_returns400() throws Exception {
        when(simulationControlService.setSpeed(any(SetSimulationSpeedRequest.class)))
                .thenThrow(new InvalidRequestException("Simulation controls require spring profile 'demo'"));

        mockMvc.perform(post("/api/v1/simulation/speed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"speed\":2}"))
                .andExpect(status().isBadRequest());
    }
}
