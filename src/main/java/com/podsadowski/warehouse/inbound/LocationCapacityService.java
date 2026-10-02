package com.podsadowski.warehouse.inbound;

import com.podsadowski.warehouse.domain.entity.Product;
import com.podsadowski.warehouse.domain.entity.StockLevel;
import com.podsadowski.warehouse.domain.entity.StorageLocation;
import com.podsadowski.warehouse.domain.repository.StockLevelRepository;
import com.podsadowski.warehouse.domain.repository.StorageLocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Bin capacity rules for putaway: mono-SKU locations.
 */
@Service
@RequiredArgsConstructor
public class LocationCapacityService {

    private final StockLevelRepository stockLevelRepository;
    private final StorageLocationRepository storageLocationRepository;

    public int freeCapacity(StorageLocation location, Product product) {
        if (location == null || product == null) {
            return 0;
        }
        int capacity = Math.max(1, product.getUnitsPerBin());
        List<StockLevel> onLocation = stockLevelRepository.findByLocationId(location.getId());
        Optional<StockLevel> sameSku = Optional.empty();
        for (StockLevel level : onLocation) {
            if (level.getQuantity() <= 0) {
                continue;
            }
            if (level.getProduct().getId().equals(product.getId())) {
                sameSku = Optional.of(level);
            } else {
                return 0;
            }
        }
        if (sameSku.isPresent()) {
            return Math.max(0, capacity - sameSku.get().getQuantity());
        }
        return capacity;
    }

    public List<StorageLocation> findPutawayTargets(Product product, Long preferredLocationId) {
        List<StorageLocation> targets = new ArrayList<>();
        Set<Long> seen = new HashSet<>();

        if (preferredLocationId != null) {
            storageLocationRepository.findById(preferredLocationId).ifPresent(preferred -> {
                if (freeCapacity(preferred, product) > 0) {
                    targets.add(preferred);
                    seen.add(preferred.getId());
                }
            });
        }

        List<StockLevel> sameSku = stockLevelRepository.findByProductId(product.getId()).stream()
                .filter(level -> level.getQuantity() > 0)
                .filter(level -> freeCapacity(level.getLocation(), product) > 0)
                .sorted(Comparator.comparingInt(StockLevel::getQuantity).reversed())
                .toList();

        for (StockLevel level : sameSku) {
            StorageLocation location = level.getLocation();
            if (seen.add(location.getId())) {
                targets.add(location);
            }
        }

        for (StorageLocation location : storageLocationRepository.findAll()) {
            if (seen.contains(location.getId())) {
                continue;
            }
            if (isEmpty(location) && freeCapacity(location, product) > 0) {
                targets.add(location);
                seen.add(location.getId());
            }
        }

        return targets;
    }

    public Optional<StorageLocation> findNextLocationWithSpace(Product product, Long preferredLocationId) {
        for (StorageLocation location : findPutawayTargets(product, preferredLocationId)) {
            if (freeCapacity(location, product) > 0) {
                return Optional.of(location);
            }
        }
        return Optional.empty();
    }

    public boolean hasFreePutawaySpace() {
        return countEmptyLocations() > 0 || hasPartialBinSpace();
    }

    public long countEmptyLocations() {
        long empty = 0;
        for (StorageLocation location : storageLocationRepository.findAll()) {
            if (isEmpty(location)) {
                empty++;
            }
        }
        return empty;
    }

    private boolean hasPartialBinSpace() {
        for (StockLevel level : stockLevelRepository.findAll()) {
            if (level.getQuantity() <= 0 || level.getProduct() == null || level.getLocation() == null) {
                continue;
            }
            if (freeCapacity(level.getLocation(), level.getProduct()) > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean isEmpty(StorageLocation location) {
        return stockLevelRepository.findByLocationId(location.getId()).stream()
                .noneMatch(level -> level.getQuantity() > 0);
    }
}
