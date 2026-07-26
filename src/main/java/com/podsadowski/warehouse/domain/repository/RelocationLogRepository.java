package com.podsadowski.warehouse.domain.repository;

import com.podsadowski.warehouse.domain.entity.RelocationLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RelocationLogRepository extends JpaRepository<RelocationLog, Long> {

    Optional<RelocationLog> findByEmployeeIdAndArrivedAtIsNull(Long employeeId);

    List<RelocationLog> findAllByOrderByScheduledAtDesc(Pageable pageable);

    List<RelocationLog> findByEmployeeIdOrderByScheduledAtDesc(Long employeeId, Pageable pageable);
}
