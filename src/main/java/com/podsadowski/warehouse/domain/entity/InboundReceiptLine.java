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
@Table(name = "inbound_receipt_lines")
@Getter
@Setter
@NoArgsConstructor
public class InboundReceiptLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id", nullable = false)
    private InboundReceipt receipt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Preferred bin when known; otherwise putaway picks an existing/random location. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_location_id")
    private StorageLocation preferredLocation;

    @Column(name = "quantity_expected", nullable = false)
    private int quantityExpected;

    @Column(name = "quantity_put_away", nullable = false)
    private int quantityPutAway;

    public int remainingToPutAway() {
        return Math.max(0, quantityExpected - quantityPutAway);
    }
}
