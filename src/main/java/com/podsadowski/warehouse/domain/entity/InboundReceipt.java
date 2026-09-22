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
 * Inbound goods receipt waiting to be put away onto storage locations.
 */
@Entity
@Table(name = "inbound_receipts")
@Getter
@Setter
@NoArgsConstructor
public class InboundReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receipt_number", nullable = false, unique = true, length = 64)
    private String receiptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InboundReceiptStatus status = InboundReceiptStatus.READY_TO_PUTAWAY;

    @Column(name = "external_ref", length = 128)
    private String externalRef;

    @Column(name = "assigned_employee_id")
    private Long assignedEmployeeId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<InboundReceiptLine> lines = new ArrayList<>();

    public void addLine(InboundReceiptLine line) {
        lines.add(line);
        line.setReceipt(this);
    }

    public int remainingToPutAway() {
        return lines.stream().mapToInt(InboundReceiptLine::remainingToPutAway).sum();
    }

    public int totalExpected() {
        return lines.stream().mapToInt(InboundReceiptLine::getQuantityExpected).sum();
    }
}
