package com.podsadowski.warehouse.api.service;

import com.podsadowski.warehouse.api.dto.CreateEmployeeRequest;
import com.podsadowski.warehouse.api.dto.DepartmentResponse;
import com.podsadowski.warehouse.api.dto.EmployeeResponse;
import com.podsadowski.warehouse.api.dto.UpdateDepartmentRequest;
import com.podsadowski.warehouse.api.dto.UpdateEmployeeRequest;
import com.podsadowski.warehouse.api.exception.InvalidRequestException;
import com.podsadowski.warehouse.api.exception.ResourceNotFoundException;
import com.podsadowski.warehouse.api.mapper.WorkforceMapper;
import com.podsadowski.warehouse.domain.entity.Department;
import com.podsadowski.warehouse.domain.entity.Employee;
import com.podsadowski.warehouse.domain.repository.DepartmentRepository;
import com.podsadowski.warehouse.domain.repository.EmployeeRepository;
import com.podsadowski.warehouse.performance.EmployeePerformanceService;
import com.podsadowski.warehouse.relocation.RelocationFulfillmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkforceWriteService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeePerformanceService employeePerformanceService;
    private final RelocationFulfillmentService relocationFulfillmentService;
    private final WorkforceMapper workforceMapper;
    private final Clock clock;

    public EmployeeResponse createEmployee(CreateEmployeeRequest request) {
        Department department = findDepartment(request.departmentId());

        Employee employee = new Employee();
        employee.setFirstName(request.firstName().trim());
        employee.setLastName(request.lastName().trim());
        employee.setCurrentDepartment(department);
        employee.setBreakTier(request.breakTier());
        employee.setOnBreak(false);

        double employeeIph = employeePerformanceService.getDefaultIph(department.getName());
        employee.setCurrentIph(employeeIph);
        employee.setOrderItemsRemaining(relocationFulfillmentService.randomOrderSize());

        Employee saved = employeeRepository.save(employee);

        department.setCurrentIPH(department.getCurrentIPH() + employeeIph);
        departmentRepository.save(department);

        return workforceMapper.toEmployeeResponse(saved, LocalDateTime.now(clock));
    }

    public EmployeeResponse updateEmployee(Long id, UpdateEmployeeRequest request) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        if (request.firstName() != null) {
            String firstName = request.firstName().trim();
            if (firstName.isEmpty()) {
                throw new InvalidRequestException("firstName must not be blank");
            }
            employee.setFirstName(firstName);
        }
        if (request.lastName() != null) {
            String lastName = request.lastName().trim();
            if (lastName.isEmpty()) {
                throw new InvalidRequestException("lastName must not be blank");
            }
            employee.setLastName(lastName);
        }
        if (request.breakTier() != null) {
            employee.setBreakTier(request.breakTier());
        }
        if (request.departmentId() != null) {
            reassignDepartment(employee, request.departmentId());
        }

        Employee saved = employeeRepository.save(employee);
        return workforceMapper.toEmployeeResponse(saved, LocalDateTime.now(clock));
    }

    public void deleteEmployee(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        if (employee.hasPendingRelocation()) {
            throw new InvalidRequestException("Cannot delete employee with an open relocation");
        }

        Department department = employee.getCurrentDepartment();
        if (department != null) {
            department.setCurrentIPH(Math.max(0, department.getCurrentIPH() - employee.getCurrentIph()));
            departmentRepository.save(department);
        }

        employeeRepository.delete(employee);
    }

    public DepartmentResponse updateDepartment(Long id, UpdateDepartmentRequest request) {
        Department department = findDepartment(id);

        if (request.requiredIph() != null) {
            if (request.requiredIph() < 0) {
                throw new InvalidRequestException("requiredIph must not be negative");
            }
            department.setRequiredIPH(request.requiredIph());
        }
        if (request.targetDailyItems() != null) {
            if (request.targetDailyItems() < 0) {
                throw new InvalidRequestException("targetDailyItems must not be negative");
            }
            department.setTargetDailyItems(request.targetDailyItems());
        }
        if (request.performanceMultiplier() != null) {
            if (request.performanceMultiplier() <= 0) {
                throw new InvalidRequestException("performanceMultiplier must be positive");
            }
            department.setPerformanceMultiplier(request.performanceMultiplier());
        }

        Department saved = departmentRepository.save(department);
        long employeeCount = employeeRepository.findByCurrentDepartment(saved).size();
        return workforceMapper.toDepartmentResponse(saved, employeeCount);
    }

    private void reassignDepartment(Employee employee, Long departmentId) {
        if (employee.hasPendingRelocation()) {
            throw new InvalidRequestException("Cannot reassign employee with an open relocation");
        }

        Department newDepartment = findDepartment(departmentId);
        Department previous = employee.getCurrentDepartment();
        if (previous != null && previous.getId().equals(newDepartment.getId())) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        double previousIph = employee.getCurrentIph();
        double newIph = employeePerformanceService.predictIphAtDepartment(
                employee, newDepartment.getName(), now);

        if (previous != null) {
            previous.setCurrentIPH(Math.max(0, previous.getCurrentIPH() - previousIph));
            departmentRepository.save(previous);
        }

        newDepartment.setCurrentIPH(newDepartment.getCurrentIPH() + newIph);
        departmentRepository.save(newDepartment);

        employee.setCurrentDepartment(newDepartment);
        employee.setCurrentIph(newIph);
        employee.setOrderItemsRemaining(relocationFulfillmentService.randomOrderSize());
    }

    private Department findDepartment(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found"));
    }
}
