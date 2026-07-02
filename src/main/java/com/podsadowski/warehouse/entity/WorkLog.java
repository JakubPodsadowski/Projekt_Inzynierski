package com.podsadowski.warehouse.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "work_logs", indexes = {
        @Index(name = "idx_work_logs_employee_dept_ts",
                columnList = "employee_id, department_name, timestamp")
})
@Getter
@Setter
@NoArgsConstructor
public class WorkLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "department_name", nullable = false)
    private DepartmentName departmentName;

    @Column(name = "items_processed", nullable = false)
    private int itemsProcessed;

    @Column(name = "elapsed_seconds", nullable = false)
    private long elapsedSeconds;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
}
