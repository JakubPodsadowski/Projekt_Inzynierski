package com.podsadowski.warehouse.domain.repository;

import com.podsadowski.warehouse.domain.entity.OutboundOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboundOrderLineRepository extends JpaRepository<OutboundOrderLine, Long> {
}
