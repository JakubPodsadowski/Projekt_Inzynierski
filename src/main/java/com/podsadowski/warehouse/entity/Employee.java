package com.podsadowski.warehouse.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "employees")
@Getter
@Setter
@NoArgsConstructor
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department currentDepartment;

    @ManyToOne
    @JoinColumn(name = "target_department_id")
    private Department targetDepartment;

    @Column(name = "last_relocated_at")
    private LocalDateTime lastRelocatedAt;

    @Column(name = "relocation_count_today", nullable = false)
    private int relocationCountToday = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "break_tier", nullable = false)
    private BreakTier breakTier;

    @Column(name = "is_on_break", nullable = false)
    private boolean isOnBreak = false;

    @Column(name = "current_iph", nullable = false)
    private double currentIph = 0.0;

    @Column(name = "order_items_remaining", nullable = false)
    private int orderItemsRemaining = 0;

    @Column(name = "relocating_until")
    private LocalDateTime relocatingUntil;

    public boolean isInTransit(LocalDateTime now) {
        return relocatingUntil != null && now.isBefore(relocatingUntil);
    }

    public boolean hasPendingRelocation() {
        return targetDepartment != null;
    }

    public boolean hasActiveOrder() {
        return orderItemsRemaining > 0;
    }

    public boolean isEligibleForRelocation(LocalDateTime currentTime, long cooldownMinutes, int maxPerDay) {
        if (isOnBreak || hasPendingRelocation() || isInTransit(currentTime)) {
            return false;
        }
        if (relocationCountToday >= maxPerDay) {
            return false;
        }
        if (lastRelocatedAt == null) {
            return true;
        }
        return lastRelocatedAt.plusMinutes(cooldownMinutes).isBefore(currentTime);
    }
}
