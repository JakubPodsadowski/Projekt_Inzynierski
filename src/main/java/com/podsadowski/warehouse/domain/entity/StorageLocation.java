package com.podsadowski.warehouse.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Physical bin location in the warehouse (aisle-rack-bin).
 */
@Entity
@Table(name = "storage_locations")
@Getter
@Setter
@NoArgsConstructor
public class StorageLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-readable code, e.g. {@code A-01-02}. */
    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(length = 16)
    private String aisle;

    @Column(length = 16)
    private String rack;

    @Column(length = 16)
    private String bin;
}
