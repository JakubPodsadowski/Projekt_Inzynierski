package com.podsadowski.warehouse.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Warehouse department with required and current aggregate IPH.
 * Deficit = {@code requiredIPH - (currentIPH × performanceMultiplier)}.
 */
@Entity
@Table(name = "departments")
@Getter
@Setter
@NoArgsConstructor
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private DepartmentName name;

    @Column(name = "target_daily_items")
    private int targetDailyItems;

    @Column(name = "current_iph")
    private double currentIPH;

    @Column(name = "required_iph")
    private double requiredIPH;

    @Column(name = "performance_multiplier", nullable = false)
    private double performanceMultiplier = 1.0;
}