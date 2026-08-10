package com.podsadowski.warehouse.api.controller;

import com.podsadowski.warehouse.api.dto.BalanceResponse;
import com.podsadowski.warehouse.api.dto.CreateEmployeeRequest;
import com.podsadowski.warehouse.api.dto.DepartmentResponse;
import com.podsadowski.warehouse.api.dto.EmployeeResponse;
import com.podsadowski.warehouse.api.dto.ForceRelocationRequest;
import com.podsadowski.warehouse.api.dto.RelocationLogResponse;
import com.podsadowski.warehouse.api.dto.RelocationStatus;
import com.podsadowski.warehouse.api.dto.UpdateDepartmentRequest;
import com.podsadowski.warehouse.api.dto.UpdateEmployeeRequest;
import com.podsadowski.warehouse.api.exception.GlobalExceptionHandler;
import com.podsadowski.warehouse.api.exception.InvalidRequestException;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({EmployeeController.class, DepartmentController.class, RelocationController.class})
@Import(GlobalExceptionHandler.class)
class WorkforceWriteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkforceReadService workforceReadService;

    @MockitoBean
    private WorkforceWriteService workforceWriteService;

    @MockitoBean
    private RelocationWriteService relocationWriteService;

    @Test
    void createEmployee_returns201() throws Exception {
        when(workforceWriteService.createEmployee(any(CreateEmployeeRequest.class)))
                .thenReturn(sampleEmployee());

        mockMvc.perform(post("/api/v1/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Jan",
                                  "lastName": "Kowalski",
                                  "departmentId": 1,
                                  "breakTier": "FIRST_SHIFT"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.firstName").value("Jan"));
    }

    @Test
    void createEmployee_validationError_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "",
                                  "lastName": "Kowalski",
                                  "departmentId": 1,
                                  "breakTier": "FIRST_SHIFT"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void updateEmployee_returnsUpdated() throws Exception {
        when(workforceWriteService.updateEmployee(eq(5L), any(UpdateEmployeeRequest.class)))
                .thenReturn(sampleEmployee());

        mockMvc.perform(patch("/api/v1/employees/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Jan"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void deleteEmployee_returns204() throws Exception {
        doNothing().when(workforceWriteService).deleteEmployee(5L);

        mockMvc.perform(delete("/api/v1/employees/5"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteEmployee_whileRelocating_returns400() throws Exception {
        doThrow(new InvalidRequestException("Cannot delete employee with an open relocation"))
                .when(workforceWriteService).deleteEmployee(5L);

        mockMvc.perform(delete("/api/v1/employees/5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Cannot delete employee with an open relocation"));
    }

    @Test
    void updateDepartment_returnsUpdated() throws Exception {
        when(workforceWriteService.updateDepartment(eq(1L), any(UpdateDepartmentRequest.class)))
                .thenReturn(new DepartmentResponse(1L, DepartmentName.PICKING, 300, 450, 1.1, 120, 1200, 10));

        mockMvc.perform(patch("/api/v1/departments/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requiredIph": 450,
                                  "performanceMultiplier": 1.1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiredIph").value(450))
                .andExpect(jsonPath("$.performanceMultiplier").value(1.1));
    }

    @Test
    void forceSchedule_returns201() throws Exception {
        when(relocationWriteService.forceSchedule(any(ForceRelocationRequest.class)))
                .thenReturn(sampleRelocationLog(RelocationLogStatus.SCHEDULED));

        mockMvc.perform(post("/api/v1/relocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "employeeId": 5,
                                  "toDepartmentId": 2
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.employeeId").value(5));
    }

    @Test
    void forceSchedule_sameDepartment_returns400() throws Exception {
        when(relocationWriteService.forceSchedule(any(ForceRelocationRequest.class)))
                .thenThrow(new InvalidRequestException("Employee is already in the target department"));

        mockMvc.perform(post("/api/v1/relocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "employeeId": 5,
                                  "toDepartmentId": 1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Employee is already in the target department"));
    }

    @Test
    void cancelRelocation_returnsCancelled() throws Exception {
        when(relocationWriteService.cancel(10L))
                .thenReturn(sampleRelocationLog(RelocationLogStatus.CANCELLED));

        mockMvc.perform(post("/api/v1/relocations/10/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelCompletedRelocation_returns400() throws Exception {
        when(relocationWriteService.cancel(10L))
                .thenThrow(new InvalidRequestException(
                        "Only SCHEDULED or IN_TRANSIT relocations can be cancelled"));

        mockMvc.perform(post("/api/v1/relocations/10/cancel"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelMissingRelocation_returns404() throws Exception {
        when(relocationWriteService.cancel(99L))
                .thenThrow(new ResourceNotFoundException("Relocation log not found"));

        mockMvc.perform(post("/api/v1/relocations/99/cancel"))
                .andExpect(status().isNotFound());
    }

    @Test
    void balance_returnsScheduledCount() throws Exception {
        when(relocationWriteService.balance()).thenReturn(new BalanceResponse(2));

        mockMvc.perform(post("/api/v1/relocations/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduled").value(2));
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
                RelocationStatus.NONE,
                null,
                null,
                null,
                0,
                12
        );
    }

    private RelocationLogResponse sampleRelocationLog(RelocationLogStatus status) {
        return new RelocationLogResponse(
                10L,
                5L,
                DepartmentName.PICKING,
                DepartmentName.PUTAWAY,
                status,
                LocalDateTime.of(2026, 7, 14, 8, 0),
                null,
                null,
                82.3,
                38.7,
                null
        );
    }
}
