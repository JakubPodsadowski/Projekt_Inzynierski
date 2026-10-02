package com.podsadowski.warehouse.inbound;

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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationCapacityServiceTest {

    @Mock
    private StockLevelRepository stockLevelRepository;
    @Mock
    private StorageLocationRepository storageLocationRepository;

    private LocationCapacityService service;

    private Product shoes;
    private Product carton;
    private StorageLocation locA;
    private StorageLocation locB;
    private StorageLocation locC;

    @BeforeEach
    void setUp() {
        service = new LocationCapacityService(stockLevelRepository, storageLocationRepository);

        shoes = product(1L, "SKU-SHOES", 6);
        carton = product(2L, "SKU-CARTON", 1);
        locA = location(10L, "A-01-01");
        locB = location(11L, "A-01-02");
        locC = location(12L, "A-01-03");
    }

    @Test
    void freeCapacity_emptyBin_returnsUnitsPerBin() {
        when(stockLevelRepository.findByLocationId(10L)).thenReturn(List.of());

        assertThat(service.freeCapacity(locA, shoes)).isEqualTo(6);
        assertThat(service.freeCapacity(locA, carton)).isEqualTo(1);
    }

    @Test
    void freeCapacity_sameSku_returnsRemainingSlots() {
        when(stockLevelRepository.findByLocationId(10L)).thenReturn(List.of(stock(shoes, locA, 4)));

        assertThat(service.freeCapacity(locA, shoes)).isEqualTo(2);
    }

    @Test
    void freeCapacity_otherSkuBlocksBin() {
        when(stockLevelRepository.findByLocationId(10L)).thenReturn(List.of(stock(carton, locA, 1)));

        assertThat(service.freeCapacity(locA, shoes)).isEqualTo(0);
    }

    @Test
    void findPutawayTargets_prefersPreferredThenSameSkuThenEmpty() {
        when(storageLocationRepository.findById(11L)).thenReturn(Optional.of(locB));
        when(stockLevelRepository.findByLocationId(11L)).thenReturn(List.of(stock(shoes, locB, 2)));
        when(stockLevelRepository.findByProductId(1L)).thenReturn(List.of(
                stock(shoes, locA, 5),
                stock(shoes, locB, 2)
        ));
        when(stockLevelRepository.findByLocationId(10L)).thenReturn(List.of(stock(shoes, locA, 5)));
        when(storageLocationRepository.findAll()).thenReturn(List.of(locA, locB, locC));
        when(stockLevelRepository.findByLocationId(12L)).thenReturn(List.of());

        List<StorageLocation> targets = service.findPutawayTargets(shoes, 11L);

        assertThat(targets).extracting(StorageLocation::getCode)
                .containsExactly("A-01-02", "A-01-01", "A-01-03");
    }

    @Test
    void findNextLocationWithSpace_skipsFullPreferred() {
        when(storageLocationRepository.findById(10L)).thenReturn(Optional.of(locA));
        when(stockLevelRepository.findByLocationId(10L)).thenReturn(List.of(stock(shoes, locA, 6)));
        when(stockLevelRepository.findByProductId(1L)).thenReturn(List.of(stock(shoes, locA, 6)));
        when(storageLocationRepository.findAll()).thenReturn(List.of(locA, locB));
        when(stockLevelRepository.findByLocationId(11L)).thenReturn(List.of());

        assertThat(service.findNextLocationWithSpace(shoes, 10L))
                .isPresent()
                .get()
                .extracting(StorageLocation::getCode)
                .isEqualTo("A-01-02");
    }

    @Test
    void hasFreePutawaySpace_trueWhenEmptyBinExists() {
        when(storageLocationRepository.findAll()).thenReturn(List.of(locA, locB));
        when(stockLevelRepository.findByLocationId(10L)).thenReturn(List.of(stock(shoes, locA, 6)));
        when(stockLevelRepository.findByLocationId(11L)).thenReturn(List.of());

        assertThat(service.hasFreePutawaySpace()).isTrue();
        assertThat(service.countEmptyLocations()).isEqualTo(1);
    }

    @Test
    void hasFreePutawaySpace_falseWhenAllBinsFull() {
        when(storageLocationRepository.findAll()).thenReturn(List.of(locA));
        when(stockLevelRepository.findByLocationId(10L)).thenReturn(List.of(stock(shoes, locA, 6)));
        when(stockLevelRepository.findAll()).thenReturn(List.of(stock(shoes, locA, 6)));

        assertThat(service.hasFreePutawaySpace()).isFalse();
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
