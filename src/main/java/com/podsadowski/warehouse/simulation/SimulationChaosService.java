package com.podsadowski.warehouse.simulation;

import com.podsadowski.warehouse.api.exception.InvalidRequestException;
import com.podsadowski.warehouse.order.OutboundOrderGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo chaos helpers that deliberately unbalance the warehouse for presentations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SimulationChaosService {

    public static final int MIN_SURGE_ITEMS = 1;
    public static final int MAX_SURGE_ITEMS = 2000;
    public static final int DEFAULT_SURGE_ITEMS = 500;

    private final OutboundOrderGenerator outboundOrderGenerator;

    /**
     * Floods the pick queue with new outbound orders totaling roughly {@code targetItems} units.
     */
    @Transactional
    public SurgeResult surgeOrders(Integer targetItems) {
        int items = targetItems != null ? targetItems : DEFAULT_SURGE_ITEMS;
        if (items < MIN_SURGE_ITEMS || items > MAX_SURGE_ITEMS) {
            throw new InvalidRequestException(
                    "itemsPerEmployee must be between " + MIN_SURGE_ITEMS + " and " + MAX_SURGE_ITEMS);
        }

        OutboundOrderGenerator.SurgeResult result = outboundOrderGenerator.surgeItems(items);
        log.info("Order surge: {} orders / {} line items (requested {})",
                result.ordersCreated(), result.itemsCreated(), items);
        return new SurgeResult(result.itemsCreated(), result.ordersCreated());
    }

    public record SurgeResult(int itemsCreated, int ordersCreated) {
    }
}
