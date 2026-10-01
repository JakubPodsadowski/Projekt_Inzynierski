package com.podsadowski.warehouse.api.service;

import com.podsadowski.warehouse.api.dto.InventoryBinResponse;
import com.podsadowski.warehouse.domain.entity.Product;
import com.podsadowski.warehouse.domain.entity.StockLevel;
import com.podsadowski.warehouse.domain.entity.StorageLocation;
import com.podsadowski.warehouse.domain.repository.StockLevelRepository;
import com.podsadowski.warehouse.domain.repository.StorageLocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryReadService {

    private final StorageLocationRepository storageLocationRepository;
    private final StockLevelRepository stockLevelRepository;

    public List<InventoryBinResponse> findInventory(String sku, String locationCode, Boolean occupiedOnly) {
        String skuFilter = normalize(sku);
        String codeFilter = normalize(locationCode);

        Map<Long, List<StockLevel>> stockByLocation = stockLevelRepository.findAll().stream()
                .filter(level -> level.getQuantity() > 0)
                .collect(Collectors.groupingBy(level -> level.getLocation().getId()));

        List<StorageLocation> locations = storageLocationRepository.findAll().stream()
                .sorted(Comparator.comparing(StorageLocation::getCode, Comparator.nullsLast(String::compareTo)))
                .toList();

        List<InventoryBinResponse> rows = new ArrayList<>();
        for (StorageLocation location : locations) {
            List<StockLevel> levels = stockByLocation.getOrDefault(location.getId(), List.of());
            if (levels.isEmpty()) {
                if (Boolean.TRUE.equals(occupiedOnly)) {
                    continue;
                }
                InventoryBinResponse empty = toEmptyRow(location);
                if (matchesFilters(empty, skuFilter, codeFilter)) {
                    rows.add(empty);
                }
                continue;
            }
            for (StockLevel level : levels) {
                InventoryBinResponse row = toStockedRow(location, level);
                if (matchesFilters(row, skuFilter, codeFilter)) {
                    if (Boolean.TRUE.equals(occupiedOnly) && row.empty()) {
                        continue;
                    }
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private static InventoryBinResponse toEmptyRow(StorageLocation location) {
        return new InventoryBinResponse(
                location.getId(),
                location.getCode(),
                location.getAisle(),
                location.getRack(),
                location.getBin(),
                null,
                null,
                null,
                0,
                null,
                0.0,
                true
        );
    }

    private static InventoryBinResponse toStockedRow(StorageLocation location, StockLevel level) {
        Product product = level.getProduct();
        int capacity = Math.max(1, product.getUnitsPerBin());
        int qty = Math.max(0, level.getQuantity());
        double fill = Math.min(100.0, (100.0 * qty) / capacity);
        return new InventoryBinResponse(
                location.getId(),
                location.getCode(),
                location.getAisle(),
                location.getRack(),
                location.getBin(),
                product.getId(),
                product.getSku(),
                product.getName(),
                qty,
                capacity,
                Math.round(fill * 10.0) / 10.0,
                false
        );
    }

    private static boolean matchesFilters(InventoryBinResponse row, String skuFilter, String codeFilter) {
        if (codeFilter != null) {
            String code = row.locationCode() == null ? "" : row.locationCode().toLowerCase(Locale.ROOT);
            if (!code.contains(codeFilter)) {
                return false;
            }
        }
        if (skuFilter != null) {
            if (row.empty()) {
                return false;
            }
            String sku = row.productSku() == null ? "" : row.productSku().toLowerCase(Locale.ROOT);
            String name = row.productName() == null ? "" : row.productName().toLowerCase(Locale.ROOT);
            if (!sku.contains(skuFilter) && !name.contains(skuFilter)) {
                return false;
            }
        }
        return true;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }
}
