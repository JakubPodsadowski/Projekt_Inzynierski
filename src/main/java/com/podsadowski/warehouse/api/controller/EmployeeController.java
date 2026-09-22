package com.podsadowski.warehouse.api.controller;

import com.podsadowski.warehouse.api.dto.CreateEmployeeRequest;
import com.podsadowski.warehouse.api.dto.EmployeeResponse;
import com.podsadowski.warehouse.api.dto.RelocationLogResponse;
import com.podsadowski.warehouse.api.dto.RelocationStatus;
import com.podsadowski.warehouse.api.dto.UpdateEmployeeRequest;
import com.podsadowski.warehouse.api.dto.WorkActivityResponse;
import com.podsadowski.warehouse.api.service.WorkforceReadService;
import com.podsadowski.warehouse.api.service.WorkforceWriteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EmployeeController {

    private final WorkforceReadService workforceReadService;
    private final WorkforceWriteService workforceWriteService;

    @GetMapping("/employees")
    public List<EmployeeResponse> getEmployees(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) RelocationStatus relocationStatus,
            @RequestParam(required = false) Boolean onBreak
    ) {
        return workforceReadService.findAllEmployees(departmentId, relocationStatus, onBreak);
    }

    @GetMapping("/employees/{id}")
    public EmployeeResponse getEmployeeById(@PathVariable Long id) {
        return workforceReadService.findEmployeeById(id);
    }

    @PostMapping("/employees")
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeResponse createEmployee(@Valid @RequestBody CreateEmployeeRequest request) {
        return workforceWriteService.createEmployee(request);
    }

    @PatchMapping("/employees/{id}")
    public EmployeeResponse updateEmployee(
            @PathVariable Long id,
            @RequestBody UpdateEmployeeRequest request
    ) {
        return workforceWriteService.updateEmployee(id, request);
    }

    @DeleteMapping("/employees/{id}")
    public ResponseEntity<Void> deleteEmployee(@PathVariable Long id) {
        workforceWriteService.deleteEmployee(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/departments/{departmentId}/employees")
    public List<EmployeeResponse> getEmployeesByDepartment(@PathVariable Long departmentId) {
        return workforceReadService.findEmployeesByDepartmentId(departmentId);
    }

    @GetMapping("/employees/{id}/relocations")
    public List<RelocationLogResponse> getEmployeeRelocations(
            @PathVariable Long id,
            @RequestParam(required = false) Integer limit
    ) {
        return workforceReadService.findEmployeeRelocationHistory(id, limit);
    }

    @GetMapping("/employees/{id}/activities")
    public List<WorkActivityResponse> getEmployeeActivities(
            @PathVariable Long id,
            @RequestParam(required = false) Integer limit
    ) {
        return workforceReadService.findEmployeeActivities(id, limit);
    }
}
