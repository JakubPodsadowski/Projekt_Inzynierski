package com.podsadowski.warehouse.api.controller;

import com.podsadowski.warehouse.api.dto.DepartmentResponse;
import com.podsadowski.warehouse.api.service.WorkforceReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final WorkforceReadService workforceReadService;

    @GetMapping
    public List<DepartmentResponse> getDepartments(
            @RequestParam(required = false, defaultValue = "deficit,desc") String sort
    ) {
        return workforceReadService.findAllDepartments(sort);
    }

    @GetMapping("/{id}")
    public DepartmentResponse getDepartmentById(@PathVariable Long id) {
        return workforceReadService.findDepartmentById(id);
    }

    @GetMapping("/by-name/{name}")
    public DepartmentResponse getDepartmentByName(@PathVariable String name) {
        return workforceReadService.findDepartmentByName(name);
    }
}
