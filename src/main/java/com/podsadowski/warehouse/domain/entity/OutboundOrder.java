package com.podsadowski.warehouse.domain.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Outbound warehouse order flowing through pick queue then pack queue.
 */
@Entity
@Table(name = "outbound_orders")
@Getter
@Setter
@NoArgsConstructor
public class OutboundOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 64)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OutboundOrderStatus status = OutboundOrderStatus.READY_TO_PICK;

    /** Higher score is claimed first by pickers. */
    @Column(name = "priority_score", nullable = false)
    private int priorityScore;

    @Column(name = "due_at")
    private LocalDateTime dueAt;

    @Column(nullable = false)
    private boolean vip = false;

    @Column(name = "external_ref", length = 128)
    private String externalRef;

    @Column(name = "assigned_employee_id")
    private Long assignedEmployeeId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "picking_started_at")
    private LocalDateTime pickingStartedAt;

    @Column(name = "picking_completed_at")
    private LocalDateTime pickingCompletedAt;

    @Column(name = "packing_started_at")
    private LocalDateTime packingStartedAt;

    @Column(name = "packing_completed_at")
    private LocalDateTime packingCompletedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OutboundOrderLine> lines = new ArrayList<>();

    public void addLine(OutboundOrderLine line) {
        lines.add(line);
        line.setOrder(this);
    }

    public int remainingToPick() {
        return lines.stream().mapToInt(OutboundOrderLine::remainingToPick).sum();
    }

    public int remainingToPack() {
        return lines.stream().mapToInt(OutboundOrderLine::remainingToPack).sum();
    }

    public int totalOrdered() {
        return lines.stream().mapToInt(OutboundOrderLine::getQuantityOrdered).sum();
    }
}
