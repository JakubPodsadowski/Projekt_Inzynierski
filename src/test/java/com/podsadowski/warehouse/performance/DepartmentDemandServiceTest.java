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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentDemandServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private OutboundOrderRepository outboundOrderRepository;
    @Mock
    private InboundReceiptRepository inboundReceiptRepository;
    @Mock
    private LocationCapacityService locationCapacityService;

    private DepartmentDemandService service;

    private Department picking;
    private Department packing;
    private Department putaway;

    // Simulated 08:00 → 16h left until midnight
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-26T08:00:00Z"), ZoneId.of("UTC"));

    @BeforeEach
    void setUp() {
        WarehouseProperties warehouseProperties = new WarehouseProperties();
        warehouseProperties.getDemand().setMinRequiredIphWhenBacklog(40.0);
        warehouseProperties.getDemand().setMinPutawayStaffWhenFreeCapacity(2.0);
        warehouseProperties.getPacking().setMaxStations(8);

        service = new DepartmentDemandService(
                departmentRepository,
                outboundOrderRepository,
                inboundReceiptRepository,
                locationCapacityService,
                warehouseProperties,
                clock
        );

        picking = department(1L, DepartmentName.PICKING);
        packing = department(2L, DepartmentName.PACKING);
        putaway = department(3L, DepartmentName.PUTAWAY);
    }

    @Test
    void hoursUntilEndOfDay_fromEightAm_isSixteen() {
        assertThat(DepartmentDemandService.hoursUntilEndOfDay(LocalDateTime.of(2026, 7, 26, 8, 0)))
                .isEqualTo(16.0);
    }

    @Test
    void requiredIph_usesBacklogOverHorizon_withMinFloor() {
        assertThat(DepartmentDemandService.requiredIph(0, 16.0, 40.0)).isEqualTo(0.0);
        assertThat(DepartmentDemandService.requiredIph(20, 16.0, 40.0)).isEqualTo(40.0);
        assertThat(DepartmentDemandService.requiredIph(800, 16.0, 40.0)).isEqualTo(50.0);
        assertThat(DepartmentDemandService.requiredIph(200, 2.0, 40.0)).isEqualTo(100.0);
    }

    @Test
    void updateRequiredIphFromPipeline_spreadsBacklogOverRestOfDay() {
        when(outboundOrderRepository.sumRemainingToPick(any())).thenReturn(50L);
        when(outboundOrderRepository.sumRemainingToPack(any())).thenReturn(800L);
        when(inboundReceiptRepository.sumRemainingToPutAway(any())).thenReturn(0L);
        when(locationCapacityService.hasFreePutawaySpace()).thenReturn(false);
        when(departmentRepository.findAll()).thenReturn(List.of(picking, packing, putaway));

        service.updateRequiredIphFromPipeline();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Department>> captor = ArgumentCaptor.forClass(List.class);
        verify(departmentRepository).saveAll(captor.capture());

        List<Department> saved = captor.getValue();
        Department savedPacking = saved.stream()
                .filter(d -> d.getName() == DepartmentName.PACKING)
                .findFirst()
                .orElseThrow();
        Department savedPicking = saved.stream()
                .filter(d -> d.getName() == DepartmentName.PICKING)
                .findFirst()
                .orElseThrow();
        Department savedPutaway = saved.stream()
                .filter(d -> d.getName() == DepartmentName.PUTAWAY)
                .findFirst()
                .orElseThrow();

        // 800 / 16h = 50; pick 50/16 < floor → 40
        assertThat(savedPacking.getRequiredIPH()).isEqualTo(50.0);
        assertThat(savedPicking.getRequiredIPH()).isEqualTo(40.0);
        assertThat(savedPutaway.getRequiredIPH()).isEqualTo(0.0);

        verify(outboundOrderRepository).sumRemainingToPick(eq(EnumSet.of(
                OutboundOrderStatus.READY_TO_PICK, OutboundOrderStatus.PICKING)));
        verify(outboundOrderRepository).sumRemainingToPack(eq(EnumSet.of(
                OutboundOrderStatus.READY_TO_PACK, OutboundOrderStatus.PACKING)));
        verify(inboundReceiptRepository).sumRemainingToPutAway(eq(EnumSet.of(
                InboundReceiptStatus.READY_TO_PUTAWAY, InboundReceiptStatus.IN_PROGRESS)));
    }

    @Test
    void updateRequiredIphFromPipeline_putawayFloorWhenFreeCapacityEvenWithoutQueue() {
        when(outboundOrderRepository.sumRemainingToPick(any())).thenReturn(0L);
        when(outboundOrderRepository.sumRemainingToPack(any())).thenReturn(0L);
        when(inboundReceiptRepository.sumRemainingToPutAway(any())).thenReturn(0L);
        when(locationCapacityService.hasFreePutawaySpace()).thenReturn(true);
        when(departmentRepository.findAll()).thenReturn(List.of(picking, packing, putaway));

        service.updateRequiredIphFromPipeline();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Department>> captor = ArgumentCaptor.forClass(List.class);
        verify(departmentRepository).saveAll(captor.capture());

        Department savedPutaway = captor.getValue().stream()
                .filter(d -> d.getName() == DepartmentName.PUTAWAY)
                .findFirst()
                .orElseThrow();
        assertThat(savedPutaway.getRequiredIPH()).isEqualTo(110.0);
    }

    @Test
    void updateRequiredIphFromPipeline_packingDemandCappedByMaxStations() {
        when(outboundOrderRepository.sumRemainingToPick(any())).thenReturn(0L);
        when(outboundOrderRepository.sumRemainingToPack(any())).thenReturn(50_000L);
        when(inboundReceiptRepository.sumRemainingToPutAway(any())).thenReturn(0L);
        when(locationCapacityService.hasFreePutawaySpace()).thenReturn(false);
        when(departmentRepository.findAll()).thenReturn(List.of(picking, packing, putaway));

        service.updateRequiredIphFromPipeline();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Department>> captor = ArgumentCaptor.forClass(List.class);
        verify(departmentRepository).saveAll(captor.capture());

        Department savedPacking = captor.getValue().stream()
                .filter(d -> d.getName() == DepartmentName.PACKING)
                .findFirst()
                .orElseThrow();
        assertThat(savedPacking.getRequiredIPH()).isEqualTo(1760.0);
    }

    private static Department department(Long id, DepartmentName name) {
        Department department = new Department();
        department.setId(id);
        department.setName(name);
        department.setCurrentIPH(0.0);
        department.setRequiredIPH(0.0);
        department.setPerformanceMultiplier(1.0);
        return department;
    }
}
