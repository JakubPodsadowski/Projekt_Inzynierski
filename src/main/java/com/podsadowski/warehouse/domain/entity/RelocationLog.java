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
 * Persistent audit record for a single employee relocation lifecycle.
 */
@Entity
@Table(name = "relocation_logs", indexes = {
        @Index(name = "idx_relocation_logs_employee_scheduled", columnList = "employee_id, scheduled_at"),
        @Index(name = "idx_relocation_logs_open", columnList = "employee_id, arrived_at")
})
@Getter
@Setter
@NoArgsConstructor
public class RelocationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_department", nullable = false)
    private DepartmentName fromDepartment;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_department", nullable = false)
    private DepartmentName toDepartment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RelocationLogStatus status;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "transit_started_at")
    private LocalDateTime transitStartedAt;

    @Column(name = "arrived_at")
    private LocalDateTime arrivedAt;

    @Column(name = "iph_at_source", nullable = false)
    private double iphAtSource;

    @Column(name = "predicted_iph_at_target", nullable = false)
    private double predictedIphAtTarget;

    @Column(name = "iph_on_arrival")
    private Double iphOnArrival;
}
