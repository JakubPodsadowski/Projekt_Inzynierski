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
 * Operational event for employee timeline (pick / pack / putaway).
 * Denormalized refs keep the timeline readable without heavy joins.
 */
@Entity
@Table(name = "work_activities", indexes = {
        @Index(name = "idx_work_activities_employee_ts", columnList = "employee_id, occurred_at")
})
@Getter
@Setter
@NoArgsConstructor
public class WorkActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WorkActivityType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "department_name", nullable = false, length = 32)
    private DepartmentName departmentName;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_sku", length = 64)
    private String productSku;

    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "location_code", length = 64)
    private String locationCode;

    @Column(name = "outbound_order_id")
    private Long outboundOrderId;

    @Column(name = "order_number", length = 64)
    private String orderNumber;

    @Column(name = "inbound_receipt_id")
    private Long inboundReceiptId;

    @Column(name = "receipt_number", length = 64)
    private String receiptNumber;

    @Column(length = 512)
    private String message;
}
