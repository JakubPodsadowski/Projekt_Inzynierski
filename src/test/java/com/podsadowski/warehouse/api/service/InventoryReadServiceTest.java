package com.podsadowski.warehouse.api.service;

import com.podsadowski.warehouse.api.dto.InventoryBinResponse;
import com.podsadowski.warehouse.domain.entity.Product;
import com.podsadowski.warehouse.domain.entity.StockLevel;
import com.podsadowski.warehouse.domain.entity.StorageLocation;
import com.podsadowski.warehouse.domain.repository.StockLevelRepository;
import com.podsadowski.warehouse.domain.repository.StorageLocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryReadServiceTest {

    @Mock
    private StorageLocationRepository storageLocationRepository;
    @Mock
    private StockLevelRepository stockLevelRepository;

    private InventoryReadService service;

    private StorageLocation locA;
    private StorageLocation locB;
    private Product shoes;

    @BeforeEach
    void setUp() {
        service = new InventoryReadService(storageLocationRepository, stockLevelRepository);
        locA = location(1L, "A-01-01");
        locB = location(2L, "B-02-01");
        shoes = product(10L, "SKU-SHOES", "Buty", 6);
    }

    @Test
    void findInventory_includesEmptyAndStockedBinsWithFillPercent() {
        when(storageLocationRepository.findAll()).thenReturn(List.of(locA, locB));
        when(stockLevelRepository.findAll()).thenReturn(List.of(stock(shoes, locA, 3)));

        List<InventoryBinResponse> rows = service.findInventory(null, null, null);

        assertThat(rows).hasSize(2);
        InventoryBinResponse stocked = rows.stream()
                .filter(r -> "A-01-01".equals(r.locationCode()))
                .findFirst()
                .orElseThrow();
        assertThat(stocked.empty()).isFalse();
        assertThat(stocked.quantity()).isEqualTo(3);
        assertThat(stocked.unitsPerBin()).isEqualTo(6);
        assertThat(stocked.fillPercent()).isEqualTo(50.0);

        InventoryBinResponse empty = rows.stream()
                .filter(r -> "B-02-01".equals(r.locationCode()))
                .findFirst()
                .orElseThrow();
        assertThat(empty.empty()).isTrue();
        assertThat(empty.productSku()).isNull();
    }

    @Test
    void findInventory_filtersBySkuAndOccupiedOnly() {
        when(storageLocationRepository.findAll()).thenReturn(List.of(locA, locB));
        when(stockLevelRepository.findAll()).thenReturn(List.of(stock(shoes, locA, 6)));

        List<InventoryBinResponse> filtered = service.findInventory("shoes", null, true);

        assertThat(filtered).hasSize(1);
        assertThat(filtered.getFirst().fillPercent()).isEqualTo(100.0);
        assertThat(filtered.getFirst().locationCode()).isEqualTo("A-01-01");
    }

    private static StorageLocation location(Long id, String code) {
        StorageLocation location = new StorageLocation();
        location.setId(id);
        location.setCode(code);
        String[] parts = code.split("-");
        location.setAisle(parts[0]);
        location.setRack(parts[1]);
        location.setBin(parts[2]);
        return location;
    }

    private static Product product(Long id, String sku, String name, int unitsPerBin) {
        Product product = new Product();
        product.setId(id);
        product.setSku(sku);
        product.setName(name);
        product.setUnitsPerBin(unitsPerBin);
        return product;
    }

    private static StockLevel stock(Product product, StorageLocation location, int qty) {
        StockLevel level = new StockLevel();
        level.setProduct(product);
        level.setLocation(location);
        level.setQuantity(qty);
        return level;
    }
}
