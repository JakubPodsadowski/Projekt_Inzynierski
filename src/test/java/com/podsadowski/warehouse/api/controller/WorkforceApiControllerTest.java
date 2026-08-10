package com.podsadowski.warehouse.api.controller;

import com.podsadowski.warehouse.api.dto.EmployeeResponse;
import com.podsadowski.warehouse.api.dto.OverviewResponse;
import com.podsadowski.warehouse.api.dto.RelocationLogResponse;
import com.podsadowski.warehouse.api.dto.RelocationStatus;
import com.podsadowski.warehouse.api.exception.GlobalExceptionHandler;
import com.podsadowski.warehouse.api.exception.ResourceNotFoundException;
import com.podsadowski.warehouse.api.service.RelocationWriteService;
import com.podsadowski.warehouse.api.service.WorkforceReadService;
import com.podsadowski.warehouse.api.service.WorkforceWriteService;
import com.podsadowski.warehouse.domain.entity.BreakTier;
import com.podsadowski.warehouse.domain.entity.DepartmentName;
import com.podsadowski.warehouse.domain.entity.RelocationLogStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({EmployeeController.class, RelocationController.class, OverviewController.class})
@Import(GlobalExceptionHandler.class)
class WorkforceApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkforceReadService workforceReadService;

    @MockitoBean
    private WorkforceWriteService workforceWriteService;

    @MockitoBean
    private RelocationWriteService relocationWriteService;

    @Test
    void getEmployees_returnsList() throws Exception {
        when(workforceReadService.findAllEmployees(null, null, null)).thenReturn(List.of(sampleEmployee()));

        mockMvc.perform(get("/api/v1/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].relocationStatus").value("SCHEDULED"));
    }

    @Test
    void getEmployeeById_notFound_returns404() throws Exception {
        when(workforceReadService.findEmployeeById(99L))
                .thenThrow(new ResourceNotFoundException("Employee not found"));

        mockMvc.perform(get("/api/v1/employees/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Employee not found"));
    }

    @Test
    void getPendingRelocations_returnsList() throws Exception {
        when(workforceReadService.findPendingRelocations()).thenReturn(List.of(sampleEmployee()));

        mockMvc.perform(get("/api/v1/relocations/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].targetDepartment").value("PUTAWAY"));
    }

    @Test
    void getOverview_returnsSummary() throws Exception {
        when(workforceReadService.getOverview()).thenReturn(new OverviewResponse(
                LocalDateTime.of(2026, 7, 14, 22, 0),
                120.5,
                List.of(),
                3,
                1,
                8,
                40
        ));

        mockMvc.perform(get("/api/v1/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deficitImbalance").value(120.5))
                .andExpect(jsonPath("$.totalEmployees").value(40));
    }

    @Test
    void getRelocationHistory_returnsList() throws Exception {
        when(workforceReadService.findRelocationHistory(null, null)).thenReturn(List.of(sampleRelocationLog()));

        mockMvc.perform(get("/api/v1/relocations/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].fromDepartment").value("PICKING"))
                .andExpect(jsonPath("$[0].toDepartment").value("PUTAWAY"))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));
    }

    @Test
    void getRelocationHistoryById_notFound_returns404() throws Exception {
        when(workforceReadService.findRelocationLogById(99L))
                .thenThrow(new ResourceNotFoundException("Relocation log not found"));

        mockMvc.perform(get("/api/v1/relocations/history/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Relocation log not found"));
    }

    @Test
    void getEmployeeRelocations_returnsList() throws Exception {
        when(workforceReadService.findEmployeeRelocationHistory(5L, null))
                .thenReturn(List.of(sampleRelocationLog()));

        mockMvc.perform(get("/api/v1/employees/5/relocations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employeeId").value(5));
    }

    private EmployeeResponse sampleEmployee() {
        return new EmployeeResponse(
                5L,
                "Jan",
                "Kowalski",
                DepartmentName.PICKING,
                82.3,
                BreakTier.FIRST_SHIFT,
                false,
                RelocationStatus.SCHEDULED,
                DepartmentName.PUTAWAY,
                null,
                LocalDateTime.of(2026, 7, 14, 8, 30),
                1,
                0
        );
    }

    private RelocationLogResponse sampleRelocationLog() {
        return new RelocationLogResponse(
                10L,
                5L,
                DepartmentName.PICKING,
                DepartmentName.PUTAWAY,
                RelocationLogStatus.COMPLETED,
                LocalDateTime.of(2026, 7, 14, 8, 0),
                LocalDateTime.of(2026, 7, 14, 8, 10),
                LocalDateTime.of(2026, 7, 14, 8, 25),
                82.3,
                38.7,
                38.7
        );
    }
}
