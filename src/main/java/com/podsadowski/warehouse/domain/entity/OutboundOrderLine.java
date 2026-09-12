package com.podsadowski.warehouse.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "outbound_order_lines")
@Getter
@Setter
@NoArgsConstructor
public class OutboundOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private OutboundOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "quantity_ordered", nullable = false)
    private int quantityOrdered;

    @Column(name = "quantity_picked", nullable = false)
    private int quantityPicked;

    @Column(name = "quantity_packed", nullable = false)
    private int quantityPacked;

    public int remainingToPick() {
        return Math.max(0, quantityOrdered - quantityPicked);
    }

    public int remainingToPack() {
        return Math.max(0, quantityPicked - quantityPacked);
    }
}
