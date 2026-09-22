package com.podsadowski.warehouse.inbound;

import com.podsadowski.warehouse.config.DataSourceModeHolder;
import com.podsadowski.warehouse.config.WarehouseProperties;
import com.podsadowski.warehouse.domain.entity.Product;
import com.podsadowski.warehouse.domain.entity.StockLevel;
import com.podsadowski.warehouse.domain.repository.ProductRepository;
import com.podsadowski.warehouse.domain.repository.StockLevelRepository;
import com.podsadowski.warehouse.simulation.SimulationRuntimeState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Demo generator: periodically creates inbound receipts to replenish stock via putaway.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InboundReceiptGenerator {

    private final InboundPutawayService inboundPutawayService;
    private final ProductRepository productRepository;
    private final StockLevelRepository stockLevelRepository;
    private final DataSourceModeHolder modeHolder;
    private final SimulationRuntimeState simulationRuntimeState;
    private final WarehouseProperties warehouseProperties;

    @Scheduled(fixedDelayString = "${warehouse.simulation.inbound-generate-interval-ms:6000}")
    public void generateTick() {
        if (!modeHolder.isDemo() || simulationRuntimeState.isPaused()) {
            return;
        }
        int maxOpen = warehouseProperties.getSimulation().getMaxOpenPutawayReceipts();
        if (inboundPutawayService.countReadyToPutAway() >= maxOpen) {
            return;
        }
        try {
            int created = createRandomReceipts(1);
            if (created > 0) {
                log.debug("Generated {} inbound receipt(s)", created);
            }
        } catch (Exception e) {
            log.warn("Inbound receipt generation skipped: {}", e.getMessage());
        }
    }

    public int createRandomReceipts(int count) {
        List<Product> products = productRepository.findAll();
        if (products.isEmpty()) {
            return 0;
        }

        int created = 0;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            List<InboundPutawayService.ReceiptLineRequest> lines = buildLines(products, random);
            if (lines.isEmpty()) {
                break;
            }
            try {
                inboundPutawayService.createReceipt(lines, "demo-inbound");
                created++;
            } catch (Exception e) {
                log.debug("Could not create inbound receipt: {}", e.getMessage());
                break;
            }
        }
        return created;
    }

    private List<InboundPutawayService.ReceiptLineRequest> buildLines(
            List<Product> products,
            ThreadLocalRandom random
    ) {
        int lineCount = 1 + random.nextInt(3);
        List<InboundPutawayService.ReceiptLineRequest> lines = new ArrayList<>();
        List<Product> shuffled = new ArrayList<>(products);
        java.util.Collections.shuffle(shuffled, random);

        for (Product product : shuffled) {
            if (lines.size() >= lineCount) {
                break;
            }
            // Prefer restocking products that are getting low, otherwise any catalog SKU.
            int onHand = stockLevelRepository.findByProductId(product.getId()).stream()
                    .mapToInt(StockLevel::getQuantity)
                    .sum();
            int qty = onHand < 40
                    ? 20 + random.nextInt(40)
                    : 5 + random.nextInt(15);
            lines.add(new InboundPutawayService.ReceiptLineRequest(product.getId(), qty));
        }
        return lines;
    }
}
