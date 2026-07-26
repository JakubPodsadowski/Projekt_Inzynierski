package com.podsadowski.warehouse.domain.repository;

import com.podsadowski.warehouse.domain.entity.EmployeeNotification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeNotificationRepository extends JpaRepository<EmployeeNotification, Long> {

    List<EmployeeNotification> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId, Pageable pageable);

    List<EmployeeNotification> findByEmployeeIdAndReadAtIsNullOrderByCreatedAtDesc(Long employeeId);

    long countByEmployeeIdAndReadAtIsNull(Long employeeId);
}
