package com.podsadowski.warehouse.domain.repository;

import com.podsadowski.warehouse.domain.entity.WorkActivity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkActivityRepository extends JpaRepository<WorkActivity, Long> {

    List<WorkActivity> findByEmployeeIdOrderByOccurredAtDescIdDesc(Long employeeId, Pageable pageable);
}
