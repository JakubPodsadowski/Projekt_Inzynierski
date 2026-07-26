package com.podsadowski.warehouse.api.controller;

import com.podsadowski.warehouse.api.dto.EmployeeResponse;
import com.podsadowski.warehouse.api.dto.RelocationStatus;
import com.podsadowski.warehouse.api.service.WorkforceReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EmployeeController {

    private final WorkforceReadService workforceReadService;

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

    @GetMapping("/departments/{departmentId}/employees")
    public List<EmployeeResponse> getEmployeesByDepartment(@PathVariable Long departmentId) {
        return workforceReadService.findEmployeesByDepartmentId(departmentId);
    }
}
