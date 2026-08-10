package com.podsadowski.warehouse.api.mapper;

import com.podsadowski.warehouse.api.dto.DepartmentResponse;
import com.podsadowski.warehouse.api.dto.EmployeeResponse;
import com.podsadowski.warehouse.api.dto.RelocationLogResponse;
import com.podsadowski.warehouse.api.dto.RelocationStatus;
import com.podsadowski.warehouse.domain.entity.Department;
import com.podsadowski.warehouse.domain.entity.DepartmentName;
import com.podsadowski.warehouse.domain.entity.Employee;
import com.podsadowski.warehouse.domain.entity.RelocationLog;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class WorkforceMapper {

    public double calculateDeficit(Department department) {
        return department.getRequiredIPH()
                - (department.getCurrentIPH() * department.getPerformanceMultiplier());
    }

    public DepartmentResponse toDepartmentResponse(Department department, long employeeCount) {
        return new DepartmentResponse(
                department.getId(),
                department.getName(),
                department.getCurrentIPH(),
                department.getRequiredIPH(),
                department.getPerformanceMultiplier(),
                calculateDeficit(department),
                department.getTargetDailyItems(),
                employeeCount
        );
    }

    public EmployeeResponse toEmployeeResponse(Employee employee, LocalDateTime now) {
        Long currentDepartmentId = employee.getCurrentDepartment() != null
                ? employee.getCurrentDepartment().getId()
                : null;
        DepartmentName currentDepartment = employee.getCurrentDepartment() != null
                ? employee.getCurrentDepartment().getName()
                : null;
        Long targetDepartmentId = employee.getTargetDepartment() != null
                ? employee.getTargetDepartment().getId()
                : null;
        DepartmentName targetDepartment = employee.getTargetDepartment() != null
                ? employee.getTargetDepartment().getName()
                : null;

        return new EmployeeResponse(
                employee.getId(),
                employee.getFirstName(),
                employee.getLastName(),
                currentDepartmentId,
                currentDepartment,
                employee.getCurrentIph(),
                employee.getBreakTier(),
                employee.isOnBreak(),
                resolveRelocationStatus(employee, now),
                targetDepartmentId,
                targetDepartment,
                employee.getRelocatingUntil(),
                employee.getLastRelocatedAt(),
                employee.getRelocationCountToday(),
                employee.getOrderItemsRemaining()
        );
    }

    public RelocationStatus resolveRelocationStatus(Employee employee, LocalDateTime now) {
        if (!employee.hasPendingRelocation()) {
            return RelocationStatus.NONE;
        }
        if (employee.isInTransit(now)) {
            return RelocationStatus.IN_TRANSIT;
        }
        return RelocationStatus.SCHEDULED;
    }

    public RelocationLogResponse toRelocationLogResponse(RelocationLog relocationLog) {
        return new RelocationLogResponse(
                relocationLog.getId(),
                relocationLog.getEmployeeId(),
                relocationLog.getFromDepartment(),
                relocationLog.getToDepartment(),
                relocationLog.getStatus(),
                relocationLog.getScheduledAt(),
                relocationLog.getTransitStartedAt(),
                relocationLog.getArrivedAt(),
                relocationLog.getIphAtSource(),
                relocationLog.getPredictedIphAtTarget(),
                relocationLog.getIphOnArrival()
        );
    }
}
