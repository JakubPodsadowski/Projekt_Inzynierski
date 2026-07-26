package com.podsadowski.warehouse.api.controller;

import com.podsadowski.warehouse.api.dto.EmployeeResponse;
import com.podsadowski.warehouse.api.dto.OverviewResponse;
import com.podsadowski.warehouse.api.dto.RelocationStatus;
import com.podsadowski.warehouse.api.exception.GlobalExceptionHandler;
import com.podsadowski.warehouse.api.exception.ResourceNotFoundException;
import com.podsadowski.warehouse.api.service.WorkforceReadService;
import com.podsadowski.warehouse.domain.entity.BreakTier;
import com.podsadowski.warehouse.domain.entity.DepartmentName;
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
}
