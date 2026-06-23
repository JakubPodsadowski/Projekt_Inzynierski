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

    @Column(name = "last_relocated_at")
    private LocalDateTime lastRelocatedAt;

    @Column(name = "relocation_count_today", nullable = false)
    private int relocationCountToday = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "break_tier", nullable = false)
    private BreakTier breakTier;

    @Column(name = "is_on_break", nullable = false)
    private boolean isOnBreak = false;

    public boolean isEligibleForRelocation(LocalDateTime currentTime) {
        if (lastRelocatedAt == null) {
            return true;
        }
        return lastRelocatedAt.plusMinutes(60).isBefore(currentTime);
    }
}
