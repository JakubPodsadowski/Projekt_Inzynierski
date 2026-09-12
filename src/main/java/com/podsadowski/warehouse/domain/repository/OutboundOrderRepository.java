package com.podsadowski.warehouse.domain.repository;

import com.podsadowski.warehouse.domain.entity.OutboundOrder;
import com.podsadowski.warehouse.domain.entity.OutboundOrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OutboundOrderRepository extends JpaRepository<OutboundOrder, Long> {

    Optional<OutboundOrder> findByAssignedEmployeeIdAndStatus(Long employeeId, OutboundOrderStatus status);

    long countByStatus(OutboundOrderStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT o FROM OutboundOrder o
            WHERE o.status = :status
            ORDER BY o.priorityScore DESC, o.createdAt ASC
            """)
    List<OutboundOrder> findClaimable(@Param("status") OutboundOrderStatus status, Pageable pageable);
}
