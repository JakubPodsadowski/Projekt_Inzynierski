package com.podsadowski.warehouse.wms;

import com.podsadowski.warehouse.domain.entity.Product;
import com.podsadowski.warehouse.domain.entity.StockLevel;
import com.podsadowski.warehouse.domain.repository.ProductRepository;
import com.podsadowski.warehouse.domain.repository.StockLevelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory stand-in for an external WMS/OMS.
 * Accepts injected messages and can synthesize demo demand / inbound traffic.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MockExternalWarehouseAdapter implements ExternalWarehousePort {

    private final ProductRepository productRepository;
    private final StockLevelRepository stockLevelRepository;
    private final Clock clock;

    private final ConcurrentLinkedQueue<ExternalDemandMessage> demandQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<ExternalInboundMessage> inboundQueue = new ConcurrentLinkedQueue<>();
    private final AtomicLong demandSeq = new AtomicLong(1);
    private final AtomicLong inboundSeq = new AtomicLong(1);

    @Override
    public void submitDemand(ExternalDemandMessage message) {
        demandQueue.offer(message);
        log.debug("WMS mock queued demand {}", message.externalRef());
    }

    @Override
    public void submitInbound(ExternalInboundMessage message) {
        inboundQueue.offer(message);
        log.debug("WMS mock queued inbound {}", message.externalRef());
    }

    @Override
    public List<ExternalDemandMessage> pollDemand(int max) {
        return drain(demandQueue, max);
    }

    @Override
    public List<ExternalInboundMessage> pollInbound(int max) {
        return drain(inboundQueue, max);
    }

    @Override
    public int pendingDemandCount() {
        return demandQueue.size();
    }

    @Override
    public int pendingInboundCount() {
        return inboundQueue.size();
    }

    /** Enqueue up to {@code count} synthesized demand messages (demo / seed). */
    public int enqueueSyntheticDemand(int count) {
        int created = 0;
        for (int i = 0; i < count; i++) {
            ExternalDemandMessage message = synthesizeDemand();
            if (message == null) {
                break;
            }
            submitDemand(message);
            created++;
        }
        return created;
    }

    /** Enqueue up to {@code count} synthesized inbound shipments (demo / seed). */
    public int enqueueSyntheticInbound(int count) {
        int created = 0;
        for (int i = 0; i < count; i++) {
            ExternalInboundMessage message = synthesizeInbound();
            if (message == null) {
                break;
            }
            submitInbound(message);
            created++;
        }
        return created;
    }

    /**
     * Chaos helper: enqueue demand messages totaling roughly {@code targetItems} units.
     */
    public SurgeResult enqueueDemandSurge(int targetItems) {
        int remaining = targetItems;
        int totalItems = 0;
        int orders = 0;
        int guard = 0;
        while (remaining > 0 && guard++ < 200) {
            ExternalDemandMessage message = synthesizeDemandForBudget(remaining);
            if (message == null || message.lines().isEmpty()) {
                break;
            }
            int orderItems = message.lines().stream().mapToInt(ExternalDemandMessage.ExternalLine::quantity).sum();
            submitDemand(message);
            totalItems += orderItems;
            orders++;
            remaining -= orderItems;
        }
        return new SurgeResult(totalItems, orders);
    }

    public ExternalDemandMessage synthesizeDemand() {
        List<Product> products = productRepository.findAll();
        if (products.isEmpty()) {
            return null;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<ExternalDemandMessage.ExternalLine> lines = buildDemandLines(products, random, null);
        if (lines.isEmpty()) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return new ExternalDemandMessage(
                "WMS-DEMAND-" + demandSeq.getAndIncrement(),
                random.nextDouble() < 0.15,
                now.plusMinutes(30 + random.nextInt(180)),
                lines
        );
    }

    public ExternalInboundMessage synthesizeInbound() {
        List<Product> products = productRepository.findAll();
        if (products.isEmpty()) {
            return null;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<ExternalInboundMessage.ExternalLine> lines = buildInboundLines(products, random);
        if (lines.isEmpty()) {
            return null;
        }
        return new ExternalInboundMessage("WMS-IN-" + inboundSeq.getAndIncrement(), lines);
    }

    private ExternalDemandMessage synthesizeDemandForBudget(int budget) {
        List<Product> products = productRepository.findAll();
        if (products.isEmpty()) {
            return null;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<ExternalDemandMessage.ExternalLine> lines = buildDemandLines(products, random, budget);
        if (lines.isEmpty()) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return new ExternalDemandMessage(
                "WMS-CHAOS-" + demandSeq.getAndIncrement(),
                random.nextDouble() < 0.2,
                now.plusMinutes(15 + random.nextInt(60)),
                lines
        );
    }

    private List<ExternalDemandMessage.ExternalLine> buildDemandLines(
            List<Product> products,
            ThreadLocalRandom random,
            Integer budget
    ) {
        int lineCount = budget == null ? 1 + random.nextInt(3) : Math.min(3, 1 + random.nextInt(3));
        List<ExternalDemandMessage.ExternalLine> lines = new ArrayList<>();
        List<Product> shuffled = new ArrayList<>(products);
        java.util.Collections.shuffle(shuffled, random);
        int left = budget == null ? Integer.MAX_VALUE : budget;

        for (Product product : shuffled) {
            if (lines.size() >= lineCount || left <= 0) {
                break;
            }
            int available = stockLevelRepository.findByProductId(product.getId()).stream()
                    .mapToInt(StockLevel::getQuantity)
                    .sum();
            if (available < 1) {
                continue;
            }
            int qty = budget == null
                    ? Math.min(available, 1 + random.nextInt(5))
                    : Math.min(left, Math.min(available, 1 + random.nextInt(8)));
            lines.add(new ExternalDemandMessage.ExternalLine(product.getSku(), qty));
            left -= qty;
        }
        return lines;
    }

    private List<ExternalInboundMessage.ExternalLine> buildInboundLines(
            List<Product> products,
            ThreadLocalRandom random
    ) {
        int lineCount = 1 + random.nextInt(3);
        List<ExternalInboundMessage.ExternalLine> lines = new ArrayList<>();
        List<Product> shuffled = new ArrayList<>(products);
        java.util.Collections.shuffle(shuffled, random);

        for (Product product : shuffled) {
            if (lines.size() >= lineCount) {
                break;
            }
            int onHand = stockLevelRepository.findByProductId(product.getId()).stream()
                    .mapToInt(StockLevel::getQuantity)
                    .sum();
            int qty = onHand < 40 ? 20 + random.nextInt(40) : 5 + random.nextInt(15);
            lines.add(new ExternalInboundMessage.ExternalLine(product.getSku(), qty));
        }
        return lines;
    }

    private static <T> List<T> drain(ConcurrentLinkedQueue<T> queue, int max) {
        List<T> result = new ArrayList<>();
        int limit = Math.max(0, max);
        while (result.size() < limit) {
            T item = queue.poll();
            if (item == null) {
                break;
            }
            result.add(item);
        }
        return result;
    }

    public record SurgeResult(int itemsCreated, int ordersCreated) {
    }
}
