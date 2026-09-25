package com.podsadowski.warehouse.performance;

import com.podsadowski.warehouse.config.WarehouseProperties;
import com.podsadowski.warehouse.domain.entity.Department;
import com.podsadowski.warehouse.domain.entity.DepartmentName;
import com.podsadowski.warehouse.domain.entity.InboundReceiptStatus;
import com.podsadowski.warehouse.domain.entity.OutboundOrderStatus;
import com.podsadowski.warehouse.domain.repository.DepartmentRepository;
import com.podsadowski.warehouse.domain.repository.InboundReceiptRepository;
import com.podsadowski.warehouse.domain.repository.OutboundOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;

/**
 * Drives {@link Department#getRequiredIPH()} from pipeline backlog so empty
 * departments with queued work (e.g. PACKING after pick) attract relocations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DepartmentDemandService {

    private static final EnumSet<OutboundOrderStatus> PICK_BACKLOG = EnumSet.of(
            OutboundOrderStatus.READY_TO_PICK,
            OutboundOrderStatus.PICKING
    );

    private static final EnumSet<OutboundOrderStatus> PACK_BACKLOG = EnumSet.of(
            OutboundOrderStatus.READY_TO_PACK,
            OutboundOrderStatus.PACKING
    );

    private static final EnumSet<InboundReceiptStatus> PUTAWAY_BACKLOG = EnumSet.of(
            InboundReceiptStatus.READY_TO_PUTAWAY,
            InboundReceiptStatus.IN_PROGRESS
    );

    private final DepartmentRepository departmentRepository;
    private final OutboundOrderRepository outboundOrderRepository;
    private final InboundReceiptRepository inboundReceiptRepository;
    private final WarehouseProperties warehouseProperties;

    @Transactional
    public void updateRequiredIphFromPipeline() {
        WarehouseProperties.Demand demand = warehouseProperties.getDemand();
        double horizonHours = Math.max(0.1, demand.getHorizonHours());
        double minWhenBacklog = Math.max(0.0, demand.getMinRequiredIphWhenBacklog());

        long pickBacklog = outboundOrderRepository.sumRemainingToPick(PICK_BACKLOG);
        long packBacklog = outboundOrderRepository.sumRemainingToPack(PACK_BACKLOG);
        long putawayBacklog = inboundReceiptRepository.sumRemainingToPutAway(PUTAWAY_BACKLOG);

        List<Department> departments = departmentRepository.findAll();
        for (Department department : departments) {
            long backlog = backlogFor(department.getName(), pickBacklog, packBacklog, putawayBacklog);
            double required = requiredIph(backlog, horizonHours, minWhenBacklog);
            department.setRequiredIPH(required);
        }
        departmentRepository.saveAll(departments);

        log.debug(
                "Pipeline demand IPH — pick backlog={}, pack backlog={}, putaway backlog={}, horizon={}h",
                pickBacklog, packBacklog, putawayBacklog, horizonHours
        );
    }

    static double requiredIph(long backlog, double horizonHours, double minWhenBacklog) {
        if (backlog <= 0) {
            return 0.0;
        }
        return Math.max(minWhenBacklog, backlog / horizonHours);
    }

    private static long backlogFor(
            DepartmentName name,
            long pickBacklog,
            long packBacklog,
            long putawayBacklog
    ) {
        return switch (name) {
            case PICKING -> pickBacklog;
            case PACKING -> packBacklog;
            case PUTAWAY -> putawayBacklog;
        };
    }
}
