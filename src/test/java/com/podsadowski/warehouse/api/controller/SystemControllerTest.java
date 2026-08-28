package com.podsadowski.warehouse.api.controller;

import com.podsadowski.warehouse.api.dto.SetSystemModeRequest;
import com.podsadowski.warehouse.api.dto.SystemMode;
import com.podsadowski.warehouse.api.dto.SystemModeResponse;
import com.podsadowski.warehouse.api.exception.GlobalExceptionHandler;
import com.podsadowski.warehouse.api.exception.InvalidRequestException;
import com.podsadowski.warehouse.api.service.SystemModeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemController.class)
@Import(GlobalExceptionHandler.class)
class SystemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SystemModeService systemModeService;

    @Test
    void getMode_returnsStatus() throws Exception {
        when(systemModeService.getMode())
                .thenReturn(new SystemModeResponse(SystemMode.DEMO, true, true));

        mockMvc.perform(get("/api/v1/system/mode"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("DEMO"))
                .andExpect(jsonPath("$.simulationRunning").value(true))
                .andExpect(jsonPath("$.profileDemo").value(true));
    }

    @Test
    void setMode_demo_returnsUpdated() throws Exception {
        when(systemModeService.setMode(any(SetSystemModeRequest.class)))
                .thenReturn(new SystemModeResponse(SystemMode.REAL, false, true));

        mockMvc.perform(post("/api/v1/system/mode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"REAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("REAL"));
    }

    @Test
    void setMode_demoWithoutProfile_returns400() throws Exception {
        when(systemModeService.setMode(any(SetSystemModeRequest.class)))
                .thenThrow(new InvalidRequestException("Demo mode requires spring profile 'demo'"));

        mockMvc.perform(post("/api/v1/system/mode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"DEMO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Demo mode requires spring profile 'demo'"));
    }
}
