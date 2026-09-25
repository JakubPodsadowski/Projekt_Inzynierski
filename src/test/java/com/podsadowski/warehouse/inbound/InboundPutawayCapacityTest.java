package com.podsadowski.warehouse.inbound;

import com.podsadowski.warehouse.activity.WorkActivityService;
import com.podsadowski.warehouse.domain.entity.InboundReceipt;
import com.podsadowski.warehouse.domain.entity.InboundReceiptLine;
import com.podsadowski.warehouse.domain.entity.InboundReceiptStatus;
import com.podsadowski.warehouse.domain.entity.Product;
import com.podsadowski.warehouse.domain.entity.StockLevel;
import com.podsadowski.warehouse.domain.entity.StorageLocation;
import com.podsadowski.warehouse.domain.repository.EmployeeRepository;
import com.podsadowski.warehouse.domain.repository.InboundReceiptRepository;
import com.podsadowski.warehouse.domain.repository.ProductRepository;
import com.podsadowski.warehouse.domain.repository.StockLevelRepository;
import com.podsadowski.warehouse.domain.repository.StorageLocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InboundPutawayCapacityTest {

    @Mock
    private InboundReceiptRepository inboundReceiptRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private StorageLocationRepository storageLocationRepository;
    @Mock
    private StockLevelRepository stockLevelRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private WorkActivityService workActivityService;

    private InboundPutawayService putawayService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-26T10:00:00Z"), ZoneId.of("UTC"));
    private final Map<Long, Integer> mutableStock = new HashMap<>();

    private Product shoes;
    private StorageLocation locA;
    private StorageLocation locB;
    private InboundReceipt receipt;
    private InboundReceiptLine line;

    @BeforeEach
    void setUp() {
        LocationCapacityService locationCapacityService =
                new LocationCapacityService(stockLevelRepository, storageLocationRepository);
        putawayService = new InboundPutawayService(
                inboundReceiptRepository,
                productRepository,
                storageLocationRepository,
                stockLevelRepository,
                employeeRepository,
                workActivityService,
                locationCapacityService,
                clock
        );

        shoes = product(1L, "SKU-SHOES", 6);
        locA = location(10L, "A-01-01");
        locB = location(11L, "A-01-02");

        line = new InboundReceiptLine();
        line.setId(1L);
        line.setProduct(shoes);
        line.setQuantityExpected(10);
        line.setQuantityPutAway(0);
        line.setPreferredLocation(locA);

        receipt = new InboundReceipt();
        receipt.setId(100L);
        receipt.setReceiptNumber("IN-1");
        receipt.setStatus(InboundReceiptStatus.IN_PROGRESS);
        receipt.setAssignedEmployeeId(5L);
        receipt.addLine(line);

        mutableStock.clear();
    }

    @Test
    void progressPutaway_spillsUntilBinsFull_andLeavesRemainder() {
        when(inboundReceiptRepository.findByAssignedEmployeeIdAndStatus(5L, InboundReceiptStatus.IN_PROGRESS))
                .thenReturn(Optional.of(receipt));

        when(storageLocationRepository.findById(10L)).thenReturn(Optional.of(locA));
        when(stockLevelRepository.findByLocationId(10L)).thenAnswer(inv -> levelsOn(locA));
        when(stockLevelRepository.findByLocationId(11L)).thenAnswer(inv -> levelsOn(locB));
        when(stockLevelRepository.findByProductId(1L)).thenAnswer(inv -> {
            List<StockLevel> levels = new ArrayList<>();
            levels.addAll(levelsOn(locA));
            levels.addAll(levelsOn(locB));
            return levels;
        });
        when(storageLocationRepository.findAll()).thenReturn(List.of(locA, locB));

        mutableStock.put(10L, 4);
        mutableStock.put(11L, 0);

        when(stockLevelRepository.findByProductIdAndLocationId(eq(1L), anyLong())).thenAnswer(inv -> {
            Long locationId = inv.getArgument(1);
            int qty = mutableStock.getOrDefault(locationId, 0);
            if (qty <= 0) {
                return Optional.empty();
            }
            StorageLocation loc = locationId.equals(10L) ? locA : locB;
            return Optional.of(stock(shoes, loc, qty));
        });
        when(stockLevelRepository.save(any(StockLevel.class))).thenAnswer(inv -> {
            StockLevel level = inv.getArgument(0);
            mutableStock.put(level.getLocation().getId(), level.getQuantity());
            return level;
        });
        when(inboundReceiptRepository.save(any(InboundReceipt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(employeeRepository.findById(5L)).thenReturn(Optional.empty());

        int processed = putawayService.progressPutaway(5L, 10);

        // locA: +2 → 6; locB: +6 → 6; 2 remain without space
        assertThat(processed).isEqualTo(8);
        assertThat(line.getQuantityPutAway()).isEqualTo(8);
        assertThat(receipt.getStatus()).isEqualTo(InboundReceiptStatus.IN_PROGRESS);
        assertThat(mutableStock.get(10L)).isEqualTo(6);
        assertThat(mutableStock.get(11L)).isEqualTo(6);
        verify(workActivityService, atLeastOnce()).record(any());
    }

    @Test
    void progressPutaway_doesNotMixSkuOnOccupiedBin() {
        Product other = product(99L, "SKU-OTHER", 6);
        when(inboundReceiptRepository.findByAssignedEmployeeIdAndStatus(5L, InboundReceiptStatus.IN_PROGRESS))
                .thenReturn(Optional.of(receipt));

        line.setPreferredLocation(null);
        line.setQuantityExpected(3);

        when(stockLevelRepository.findByLocationId(10L)).thenReturn(List.of(stock(other, locA, 3)));
        when(stockLevelRepository.findByLocationId(11L)).thenAnswer(inv -> levelsOn(locB));
        when(stockLevelRepository.findByProductId(1L)).thenReturn(List.of());
        when(storageLocationRepository.findAll()).thenReturn(List.of(locA, locB));

        mutableStock.put(11L, 0);
        when(stockLevelRepository.findByProductIdAndLocationId(eq(1L), eq(11L))).thenReturn(Optional.empty());
        when(stockLevelRepository.save(any(StockLevel.class))).thenAnswer(inv -> {
            StockLevel level = inv.getArgument(0);
            mutableStock.put(level.getLocation().getId(), level.getQuantity());
            return level;
        });
        when(inboundReceiptRepository.save(any(InboundReceipt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(employeeRepository.findById(5L)).thenReturn(Optional.empty());

        int processed = putawayService.progressPutaway(5L, 3);

        assertThat(processed).isEqualTo(3);
        assertThat(mutableStock.get(11L)).isEqualTo(3);
        verify(stockLevelRepository, never()).findByProductIdAndLocationId(1L, 10L);
    }

    private List<StockLevel> levelsOn(StorageLocation location) {
        int qty = mutableStock.getOrDefault(location.getId(), 0);
        if (qty <= 0) {
            return List.of();
        }
        return List.of(stock(shoes, location, qty));
    }

    private static Product product(Long id, String sku, int unitsPerBin) {
        Product product = new Product();
        product.setId(id);
        product.setSku(sku);
        product.setName(sku);
        product.setUnitsPerBin(unitsPerBin);
        return product;
    }

    private static StorageLocation location(Long id, String code) {
        StorageLocation location = new StorageLocation();
        location.setId(id);
        location.setCode(code);
        return location;
    }

    private static StockLevel stock(Product product, StorageLocation location, int qty) {
        StockLevel level = new StockLevel();
        level.setProduct(product);
        level.setLocation(location);
        level.setQuantity(qty);
        return level;
    }
}
