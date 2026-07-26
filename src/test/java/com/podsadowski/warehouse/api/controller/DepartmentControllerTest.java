package com.podsadowski.warehouse.api.controller;

import com.podsadowski.warehouse.api.dto.DepartmentResponse;
import com.podsadowski.warehouse.api.exception.GlobalExceptionHandler;
import com.podsadowski.warehouse.api.exception.InvalidRequestException;
import com.podsadowski.warehouse.api.exception.ResourceNotFoundException;
import com.podsadowski.warehouse.api.service.WorkforceReadService;
import com.podsadowski.warehouse.domain.entity.DepartmentName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DepartmentController.class)
@Import(GlobalExceptionHandler.class)
class DepartmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkforceReadService workforceReadService;

    @Test
    void getDepartments_returnsList() throws Exception {
        when(workforceReadService.findAllDepartments("deficit,desc")).thenReturn(List.of(
                new DepartmentResponse(1L, DepartmentName.PICKING, 300, 400, 1.0, 100, 1000, 10)
        ));

        mockMvc.perform(get("/api/v1/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("PICKING"))
                .andExpect(jsonPath("$[0].deficit").value(100));
    }

    @Test
    void getDepartmentById_returnsDepartment() throws Exception {
        when(workforceReadService.findDepartmentById(1L)).thenReturn(
                new DepartmentResponse(1L, DepartmentName.PICKING, 300, 400, 1.0, 100, 1000, 10)
        );

        mockMvc.perform(get("/api/v1/departments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("PICKING"));
    }

    @Test
    void getDepartmentById_notFound_returns404() throws Exception {
        when(workforceReadService.findDepartmentById(99L))
                .thenThrow(new ResourceNotFoundException("Department not found"));

        mockMvc.perform(get("/api/v1/departments/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Department not found"));
    }

    @Test
    void getDepartmentByName_invalidName_returns400() throws Exception {
        when(workforceReadService.findDepartmentByName("INVALID"))
                .thenThrow(new InvalidRequestException("Invalid department name: INVALID"));

        mockMvc.perform(get("/api/v1/departments/by-name/INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid department name: INVALID"));
    }
}
