package com.podsadowski.warehouse.inbound;

import com.podsadowski.warehouse.activity.WorkActivityService;
import com.podsadowski.warehouse.activity.WorkActivityService.WorkActivityDraft;
import com.podsadowski.warehouse.api.exception.InvalidRequestException;
import com.podsadowski.warehouse.api.exception.ResourceNotFoundException;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Creates inbound receipts and drives putaway onto storage locations (stock increases).
 * Putaway respects mono-SKU bin capacity ({@link Product#getUnitsPerBin()}) with spillover.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InboundPutawayService {

    private static final AtomicLong RECEIPT_SEQ = new AtomicLong(1);

    private final InboundReceiptRepository inboundReceiptRepository;
    private final ProductRepository productRepository;
    private final StorageLocationRepository storageLocationRepository;
    private final StockLevelRepository stockLevelRepository;
    private final EmployeeRepository employeeRepository;
    private final WorkActivityService workActivityService;
    private final LocationCapacityService locationCapacityService;
    private final Clock clock;

    @Transactional
    public InboundReceipt createReceipt(List<ReceiptLineRequest> lines, String externalRef) {
        if (lines == null || lines.isEmpty()) {
            throw new InvalidRequestException("Receipt must contain at least one line");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        InboundReceipt receipt = new InboundReceipt();
        receipt.setReceiptNumber(nextReceiptNumber(now));
        receipt.setStatus(InboundReceiptStatus.READY_TO_PUTAWAY);
        receipt.setExternalRef(externalRef);
        receipt.setCreatedAt(now);

        for (ReceiptLineRequest lineRequest : lines) {
            Product product = productRepository.findById(lineRequest.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + lineRequest.productId()));
            if (lineRequest.quantity() <= 0) {
                throw new InvalidRequestException("Line quantity must be positive");
            }

            InboundReceiptLine line = new InboundReceiptLine();
            line.setProduct(product);
            line.setQuantityExpected(lineRequest.quantity());
            line.setQuantityPutAway(0);
            if (lineRequest.preferredLocationId() != null) {
                StorageLocation location = storageLocationRepository.findById(lineRequest.preferredLocationId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Location not found: " + lineRequest.preferredLocationId()));
                line.setPreferredLocation(location);
            }
            receipt.addLine(line);
        }

        InboundReceipt saved = inboundReceiptRepository.save(receipt);
        log.debug("Created inbound receipt {} ({} items)", saved.getReceiptNumber(), saved.totalExpected());
        return saved;
    }

    @Transactional
    public Optional<InboundReceipt> ensurePutawayAssignment(Long employeeId) {
        Optional<InboundReceipt> current = inboundReceiptRepository
                .findByAssignedEmployeeIdAndStatus(employeeId, InboundReceiptStatus.IN_PROGRESS);
        if (current.isPresent()) {
            syncEmployeeRemaining(employeeId, current.get().remainingToPutAway());
            return current;
        }

        List<InboundReceipt> claimable = inboundReceiptRepository.findClaimable(
                InboundReceiptStatus.READY_TO_PUTAWAY, PageRequest.of(0, 1));
        if (claimable.isEmpty()) {
            syncEmployeeRemaining(employeeId, 0);
            return Optional.empty();
        }

        LocalDateTime now = LocalDateTime.now(clock);
        InboundReceipt receipt = claimable.getFirst();
        receipt.setStatus(InboundReceiptStatus.IN_PROGRESS);
        receipt.setAssignedEmployeeId(employeeId);
        receipt.setStartedAt(now);
        inboundReceiptRepository.save(receipt);
        syncEmployeeRemaining(employeeId, receipt.remainingToPutAway());
        workActivityService.record(WorkActivityDraft.putawayStarted(
                employeeId, receipt.getId(), receipt.getReceiptNumber()));
        log.debug("Employee {} claimed putaway receipt {}", employeeId, receipt.getReceiptNumber());
        return Optional.of(receipt);
    }

    @Transactional
    public int progressPutaway(Long employeeId, int items) {
        if (items <= 0) {
            return 0;
        }
        InboundReceipt receipt = inboundReceiptRepository
                .findByAssignedEmployeeIdAndStatus(employeeId, InboundReceiptStatus.IN_PROGRESS)
                .orElse(null);
        if (receipt == null) {
            return 0;
        }

        int remainingBudget = items;
        int processed = 0;
        List<InboundReceiptLine> lines = receipt.getLines().stream()
                .sorted(Comparator.comparingLong(line -> line.getId() == null ? 0L : line.getId()))
                .toList();

        for (InboundReceiptLine line : lines) {
            if (remainingBudget <= 0) {
                break;
            }
            Long preferredId = line.getPreferredLocation() != null ? line.getPreferredLocation().getId() : null;
            Product product = line.getProduct();

            // Exhaust this SKU (spill across bins) before moving to the next line.
            while (remainingBudget > 0 && line.remainingToPutAway() > 0) {
                Optional<StorageLocation> target = locationCapacityService.findNextLocationWithSpace(
                        product, preferredId);
                if (target.isEmpty()) {
                    log.warn(
                            "No free bin capacity for product {} (unitsPerBin={}); receipt {} stays in progress",
                            product.getSku(),
                            product.getUnitsPerBin(),
                            receipt.getReceiptNumber()
                    );
                    break;
                }

                StorageLocation location = target.get();
                int free = locationCapacityService.freeCapacity(location, product);
                int take = Math.min(line.remainingToPutAway(), Math.min(free, remainingBudget));
                if (take <= 0) {
                    break;
                }

                addStock(product, location, take);
                line.setQuantityPutAway(line.getQuantityPutAway() + take);
                workActivityService.record(WorkActivityDraft.putawayItem(
                        employeeId,
                        receipt.getId(),
                        receipt.getReceiptNumber(),
                        product.getId(),
                        product.getSku(),
                        location.getId(),
                        location.getCode(),
                        take
                ));
                processed += take;
                remainingBudget -= take;
            }
        }

        if (receipt.remainingToPutAway() == 0) {
            int totalPutAway = receipt.getLines().stream().mapToInt(InboundReceiptLine::getQuantityPutAway).sum();
            receipt.setStatus(InboundReceiptStatus.COMPLETED);
            receipt.setCompletedAt(LocalDateTime.now(clock));
            receipt.setAssignedEmployeeId(null);
            inboundReceiptRepository.save(receipt);
            workActivityService.record(WorkActivityDraft.putawayCompleted(
                    employeeId, receipt.getId(), receipt.getReceiptNumber(), totalPutAway));
            syncEmployeeRemaining(employeeId, 0);
            log.debug("Receipt {} putaway completed", receipt.getReceiptNumber());
        } else {
            inboundReceiptRepository.save(receipt);
            syncEmployeeRemaining(employeeId, receipt.remainingToPutAway());
        }
        return processed;
    }

    public long countReadyToPutAway() {
        return inboundReceiptRepository.countByStatus(InboundReceiptStatus.READY_TO_PUTAWAY);
    }

    public long countInProgress() {
        return inboundReceiptRepository.countByStatus(InboundReceiptStatus.IN_PROGRESS);
    }

    private void addStock(Product product, StorageLocation location, int quantity) {
        StockLevel level = stockLevelRepository
                .findByProductIdAndLocationId(product.getId(), location.getId())
                .orElseGet(() -> {
                    StockLevel created = new StockLevel();
                    created.setProduct(product);
                    created.setLocation(location);
                    created.setQuantity(0);
                    return created;
                });
        level.setQuantity(level.getQuantity() + quantity);
        stockLevelRepository.save(level);
    }

    private void syncEmployeeRemaining(Long employeeId, int remaining) {
        employeeRepository.findById(employeeId).ifPresent(employee -> {
            employee.setOrderItemsRemaining(Math.max(0, remaining));
            employeeRepository.save(employee);
        });
    }

    private String nextReceiptNumber(LocalDateTime now) {
        return "IN-" + now.toLocalDate() + "-" + RECEIPT_SEQ.getAndIncrement();
    }

    public record ReceiptLineRequest(long productId, int quantity, Long preferredLocationId) {
        public ReceiptLineRequest(long productId, int quantity) {
            this(productId, quantity, null);
        }
    }
}
