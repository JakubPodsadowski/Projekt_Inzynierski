package com.podsadowski.warehouse.performance;

import com.podsadowski.warehouse.config.WarehouseProperties;
import com.podsadowski.warehouse.domain.entity.Department;
import com.podsadowski.warehouse.domain.entity.DepartmentName;
import com.podsadowski.warehouse.domain.entity.InboundReceiptStatus;
import com.podsadowski.warehouse.domain.entity.OutboundOrderStatus;
import com.podsadowski.warehouse.domain.repository.DepartmentRepository;
import com.podsadowski.warehouse.domain.repository.InboundReceiptRepository;
import com.podsadowski.warehouse.domain.repository.OutboundOrderRepository;
import com.podsadowski.warehouse.inbound.LocationCapacityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Sets department requiredIPH from pipeline backlog.
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
    private final LocationCapacityService locationCapacityService;
    private final WarehouseProperties warehouseProperties;
    private final Clock clock;

    @Transactional
    public void updateRequiredIphFromPipeline() {
        WarehouseProperties.Demand demand = warehouseProperties.getDemand();
        double horizonHours = remainingHoursUntilEndOfDay();
        double minWhenBacklog = Math.max(0.0, demand.getMinRequiredIphWhenBacklog());

        long pickBacklog = outboundOrderRepository.sumRemainingToPick(PICK_BACKLOG);
        long packBacklog = outboundOrderRepository.sumRemainingToPack(PACK_BACKLOG);
        long putawayBacklog = inboundReceiptRepository.sumRemainingToPutAway(PUTAWAY_BACKLOG);
        boolean freePutawaySpace = locationCapacityService.hasFreePutawaySpace();

        Map<DepartmentName, Double> defaults = warehouseProperties.getPerformance().getDefaultIph();
        double putawayDefault = defaults.getOrDefault(DepartmentName.PUTAWAY, 55.0);
        double packingDefault = defaults.getOrDefault(DepartmentName.PACKING, 220.0);
        double putawayFloor = Math.max(0.0, demand.getMinPutawayStaffWhenFreeCapacity()) * putawayDefault;
        int maxPackStations = Math.max(1, warehouseProperties.getPacking().getMaxStations());
        double packingCap = maxPackStations * packingDefault;

        List<Department> departments = departmentRepository.findAll();
        for (Department department : departments) {
            long backlog = backlogFor(department.getName(), pickBacklog, packBacklog, putawayBacklog);
            double required = requiredIph(backlog, horizonHours, minWhenBacklog);

            if (department.getName() == DepartmentName.PUTAWAY
                    && (freePutawaySpace || putawayBacklog > 0)
                    && putawayFloor > 0) {
                required = Math.max(required, putawayFloor);
            }
            if (department.getName() == DepartmentName.PACKING && required > 0) {
                required = Math.min(required, packingCap);
            }

            department.setRequiredIPH(required);
        }
        departmentRepository.saveAll(departments);

        log.debug(
                "Pipeline demand IPH — pick={}, pack={}, putaway={}, freeBins={}, horizon={}h, packCap={}",
                pickBacklog, packBacklog, putawayBacklog, freePutawaySpace, horizonHours, packingCap
        );
    }

    static double requiredIph(long backlog, double horizonHours, double minWhenBacklog) {
        if (backlog <= 0) {
            return 0.0;
        }
        return Math.max(minWhenBacklog, backlog / horizonHours);
    }

    static double hoursUntilEndOfDay(LocalDateTime now) {
        LocalDateTime endOfDay = now.toLocalDate().plusDays(1).atStartOfDay();
        double hours = ChronoUnit.MILLIS.between(now, endOfDay) / 3_600_000.0;
        return Math.max(0.1, hours);
    }

    private double remainingHoursUntilEndOfDay() {
        return hoursUntilEndOfDay(LocalDateTime.now(clock));
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
