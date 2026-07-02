package com.podsadowski.warehouse.repository;

import com.podsadowski.warehouse.entity.WorkLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface WorkLogRepository extends JpaRepository<WorkLog, Long> {

    @Query("""
            SELECT wl.employeeId, wl.departmentName, SUM(wl.itemsProcessed), SUM(wl.elapsedSeconds)
            FROM WorkLog wl
            WHERE wl.timestamp >= :since
            GROUP BY wl.employeeId, wl.departmentName
            """)
    List<Object[]> aggregateWorkSince(@Param("since") LocalDateTime since);

    void deleteByTimestampBefore(LocalDateTime since);
}
