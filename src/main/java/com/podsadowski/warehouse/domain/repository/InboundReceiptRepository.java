package com.podsadowski.warehouse.domain.repository;

import com.podsadowski.warehouse.domain.entity.InboundReceipt;
import com.podsadowski.warehouse.domain.entity.InboundReceiptStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InboundReceiptRepository extends JpaRepository<InboundReceipt, Long> {

    Optional<InboundReceipt> findByAssignedEmployeeIdAndStatus(Long employeeId, InboundReceiptStatus status);

    long countByStatus(InboundReceiptStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r FROM InboundReceipt r
            WHERE r.status = :status
            ORDER BY r.createdAt ASC
            """)
    List<InboundReceipt> findClaimable(@Param("status") InboundReceiptStatus status, Pageable pageable);
}
