package com.podsadowski.warehouse.api.service;

import com.podsadowski.warehouse.api.dto.DepartmentResponse;
import com.podsadowski.warehouse.api.dto.EmployeeResponse;
import com.podsadowski.warehouse.api.dto.OverviewResponse;
import com.podsadowski.warehouse.api.dto.RelocationLogResponse;
import com.podsadowski.warehouse.api.dto.RelocationStatus;
import com.podsadowski.warehouse.api.dto.WorkActivityResponse;
import com.podsadowski.warehouse.api.exception.InvalidRequestException;
import com.podsadowski.warehouse.api.exception.ResourceNotFoundException;
import com.podsadowski.warehouse.api.mapper.WorkforceMapper;
import com.podsadowski.warehouse.domain.entity.Department;
import com.podsadowski.warehouse.domain.entity.DepartmentName;
import com.podsadowski.warehouse.domain.entity.Employee;
import com.podsadowski.warehouse.domain.entity.InboundReceiptStatus;
import com.podsadowski.warehouse.domain.entity.OutboundOrderStatus;
import com.podsadowski.warehouse.domain.entity.WorkActivity;
import com.podsadowski.warehouse.domain.repository.DepartmentRepository;
import com.podsadowski.warehouse.domain.repository.EmployeeRepository;
import com.podsadowski.warehouse.domain.repository.InboundReceiptRepository;
import com.podsadowski.warehouse.domain.repository.OutboundOrderRepository;
import com.podsadowski.warehouse.domain.repository.RelocationLogRepository;
import com.podsadowski.warehouse.domain.repository.WorkActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkforceReadService {

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final RelocationLogRepository relocationLogRepository;
    private final WorkActivityRepository workActivityRepository;
    private final OutboundOrderRepository outboundOrderRepository;
    private final InboundReceiptRepository inboundReceiptRepository;
    private final WorkforceMapper workforceMapper;
    private final Clock clock;

    public List<DepartmentResponse> findAllDepartments(String sort) {
        Map<Long, Long> employeeCounts = buildEmployeeCountByDepartmentId();
        Comparator<DepartmentResponse> comparator = resolveDepartmentComparator(sort);

        return departmentRepository.findAll().stream()
                .map(department -> workforceMapper.toDepartmentResponse(
                        department,
                        employeeCounts.getOrDefault(department.getId(), 0L)))
                .sorted(comparator)
                .toList();
    }

    public DepartmentResponse findDepartmentById(Long id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found"));
        long employeeCount = countEmployeesInDepartment(department.getId());
        return workforceMapper.toDepartmentResponse(department, employeeCount);
    }

    public DepartmentResponse findDepartmentByName(String name) {
        DepartmentName departmentName = parseDepartmentName(name);
        Department department = departmentRepository.findByName(departmentName)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found"));
        long employeeCount = countEmployeesInDepartment(department.getId());
        return workforceMapper.toDepartmentResponse(department, employeeCount);
    }

    public List<EmployeeResponse> findAllEmployees(
            Long departmentId,
            RelocationStatus relocationStatus,
            Boolean onBreak
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        return employeeRepository.findAll().stream()
                .filter(employee -> matchesDepartmentFilter(employee, departmentId))
                .filter(employee -> matchesRelocationFilter(employee, relocationStatus, now))
                .filter(employee -> matchesOnBreakFilter(employee, onBreak))
                .map(employee -> workforceMapper.toEmployeeResponse(employee, now))
                .toList();
    }

    public EmployeeResponse findEmployeeById(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        return workforceMapper.toEmployeeResponse(employee, LocalDateTime.now(clock));
    }

    public List<EmployeeResponse> findEmployeesByDepartmentId(Long departmentId) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found"));
        LocalDateTime now = LocalDateTime.now(clock);
        return employeeRepository.findByCurrentDepartment(department).stream()
                .map(employee -> workforceMapper.toEmployeeResponse(employee, now))
                .toList();
    }

    public List<EmployeeResponse> findPendingRelocations() {
        LocalDateTime now = LocalDateTime.now(clock);
        return employeeRepository.findAll().stream()
                .filter(Employee::hasPendingRelocation)
                .filter(employee -> !employee.isInTransit(now))
                .map(employee -> workforceMapper.toEmployeeResponse(employee, now))
                .toList();
    }

    public List<EmployeeResponse> findInTransitRelocations() {
        LocalDateTime now = LocalDateTime.now(clock);
        return employeeRepository.findAll().stream()
                .filter(employee -> employee.isInTransit(now))
                .map(employee -> workforceMapper.toEmployeeResponse(employee, now))
                .toList();
    }

    public OverviewResponse getOverview() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<DepartmentResponse> departments = findAllDepartments("deficit,desc");
        double deficitImbalance = calculateDeficitImbalance(departments);

        List<Employee> employees = employeeRepository.findAll();
        long pendingRelocations = employees.stream()
                .filter(Employee::hasPendingRelocation)
                .filter(employee -> !employee.isInTransit(now))
                .count();
        long inTransitRelocations = employees.stream()
                .filter(employee -> employee.isInTransit(now))
                .count();
        long employeesOnBreak = employees.stream()
                .filter(Employee::isOnBreak)
                .count();

        return new OverviewResponse(
                now,
                deficitImbalance,
                departments,
                pendingRelocations,
                inTransitRelocations,
                employeesOnBreak,
                employees.size(),
                outboundOrderRepository.countByStatus(OutboundOrderStatus.READY_TO_PICK),
                outboundOrderRepository.countByStatus(OutboundOrderStatus.PICKING),
                outboundOrderRepository.countByStatus(OutboundOrderStatus.READY_TO_PACK),
                outboundOrderRepository.countByStatus(OutboundOrderStatus.PACKING),
                inboundReceiptRepository.countByStatus(InboundReceiptStatus.READY_TO_PUTAWAY),
                inboundReceiptRepository.countByStatus(InboundReceiptStatus.IN_PROGRESS)
        );
    }

    public List<RelocationLogResponse> findRelocationHistory(Long employeeId, Integer limit) {
        int pageSize = resolveHistoryLimit(limit);
        PageRequest pageable = PageRequest.of(0, pageSize);

        if (employeeId != null) {
            ensureEmployeeExists(employeeId);
            return relocationLogRepository.findByEmployeeIdOrderByScheduledAtDesc(employeeId, pageable).stream()
                    .map(workforceMapper::toRelocationLogResponse)
                    .toList();
        }

        return relocationLogRepository.findAllByOrderByScheduledAtDesc(pageable).stream()
                .map(workforceMapper::toRelocationLogResponse)
                .toList();
    }

    public RelocationLogResponse findRelocationLogById(Long id) {
        return relocationLogRepository.findById(id)
                .map(workforceMapper::toRelocationLogResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Relocation log not found"));
    }

    public List<RelocationLogResponse> findEmployeeRelocationHistory(Long employeeId, Integer limit) {
        ensureEmployeeExists(employeeId);
        return findRelocationHistory(employeeId, limit);
    }

    public List<WorkActivityResponse> findEmployeeActivities(Long employeeId, Integer limit) {
        ensureEmployeeExists(employeeId);
        int pageSize = resolveHistoryLimit(limit);
        return workActivityRepository
                .findByEmployeeIdOrderByOccurredAtDescIdDesc(employeeId, PageRequest.of(0, pageSize))
                .stream()
                .map(this::toWorkActivityResponse)
                .toList();
    }

    private WorkActivityResponse toWorkActivityResponse(WorkActivity activity) {
        return new WorkActivityResponse(
                activity.getId(),
                activity.getEmployeeId(),
                activity.getType(),
                activity.getDepartmentName(),
                activity.getOccurredAt(),
                activity.getQuantity(),
                activity.getProductId(),
                activity.getProductSku(),
                activity.getLocationId(),
                activity.getLocationCode(),
                activity.getOutboundOrderId(),
                activity.getOrderNumber(),
                activity.getInboundReceiptId(),
                activity.getReceiptNumber(),
                activity.getMessage()
        );
    }

    private void ensureEmployeeExists(Long employeeId) {
        if (!employeeRepository.existsById(employeeId)) {
            throw new ResourceNotFoundException("Employee not found");
        }
    }

    private int resolveHistoryLimit(Integer limit) {
        if (limit == null) {
            return 50;
        }
        if (limit < 1) {
            throw new InvalidRequestException("limit must be at least 1");
        }
        return Math.min(limit, 200);
    }

    private DepartmentName parseDepartmentName(String name) {
        try {
            return DepartmentName.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Invalid department name: " + name);
        }
    }

    private Map<Long, Long> buildEmployeeCountByDepartmentId() {
        return employeeRepository.findAll().stream()
                .filter(employee -> employee.getCurrentDepartment() != null)
                .collect(Collectors.groupingBy(
                        employee -> employee.getCurrentDepartment().getId(),
                        Collectors.counting()
                ));
    }

    private long countEmployeesInDepartment(Long departmentId) {
        return buildEmployeeCountByDepartmentId().getOrDefault(departmentId, 0L);
    }

    private Comparator<DepartmentResponse> resolveDepartmentComparator(String sort) {
        if (sort == null || sort.isBlank() || "deficit,desc".equalsIgnoreCase(sort)) {
            return Comparator.comparingDouble(DepartmentResponse::deficit).reversed();
        }
        if ("deficit,asc".equalsIgnoreCase(sort)) {
            return Comparator.comparingDouble(DepartmentResponse::deficit);
        }
        if ("name,asc".equalsIgnoreCase(sort)) {
            return Comparator.comparing(response -> response.name().name());
        }
        throw new InvalidRequestException("Unsupported sort parameter: " + sort);
    }

    private double calculateDeficitImbalance(List<DepartmentResponse> departments) {
        if (departments.isEmpty()) {
            return 0;
        }
        double min = departments.stream().mapToDouble(DepartmentResponse::deficit).min().orElse(0);
        double max = departments.stream().mapToDouble(DepartmentResponse::deficit).max().orElse(0);
        return max - min;
    }

    private boolean matchesDepartmentFilter(Employee employee, Long departmentId) {
        if (departmentId == null) {
            return true;
        }
        return employee.getCurrentDepartment() != null
                && employee.getCurrentDepartment().getId().equals(departmentId);
    }

    private boolean matchesRelocationFilter(
            Employee employee,
            RelocationStatus relocationStatus,
            LocalDateTime now
    ) {
        if (relocationStatus == null) {
            return true;
        }
        return workforceMapper.resolveRelocationStatus(employee, now) == relocationStatus;
    }

    private boolean matchesOnBreakFilter(Employee employee, Boolean onBreak) {
        if (onBreak == null) {
            return true;
        }
        return employee.isOnBreak() == onBreak;
    }
}
