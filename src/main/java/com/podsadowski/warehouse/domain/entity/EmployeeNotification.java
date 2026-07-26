package com.podsadowski.warehouse.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Persistent employee notification, typically about relocation lifecycle events.
 */
@Entity
@Table(name = "employee_notifications", indexes = {
        @Index(name = "idx_employee_notifications_employee_created",
                columnList = "employee_id, created_at"),
        @Index(name = "idx_employee_notifications_employee_unread",
                columnList = "employee_id, read_at")
})
@Getter
@Setter
@NoArgsConstructor
public class EmployeeNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "relocation_log_id")
    private Long relocationLogId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_department")
    private DepartmentName fromDepartment;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_department")
    private DepartmentName toDepartment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;
}
