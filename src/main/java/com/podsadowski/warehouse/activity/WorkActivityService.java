package com.podsadowski.warehouse.activity;

import com.podsadowski.warehouse.domain.entity.DepartmentName;
import com.podsadowski.warehouse.domain.entity.WorkActivity;
import com.podsadowski.warehouse.domain.entity.WorkActivityType;
import com.podsadowski.warehouse.domain.repository.WorkActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Persists operational timeline events for employees.
 */
@Service
@RequiredArgsConstructor
public class WorkActivityService {

    private final WorkActivityRepository workActivityRepository;
    private final Clock clock;

    @Transactional
    public WorkActivity record(WorkActivityDraft draft) {
        WorkActivity activity = new WorkActivity();
        activity.setEmployeeId(draft.employeeId());
        activity.setType(draft.type());
        activity.setDepartmentName(draft.departmentName());
        activity.setOccurredAt(draft.occurredAt() != null ? draft.occurredAt() : LocalDateTime.now(clock));
        activity.setQuantity(Math.max(0, draft.quantity()));
        activity.setProductId(draft.productId());
        activity.setProductSku(draft.productSku());
        activity.setLocationId(draft.locationId());
        activity.setLocationCode(draft.locationCode());
        activity.setOutboundOrderId(draft.outboundOrderId());
        activity.setOrderNumber(draft.orderNumber());
        activity.setInboundReceiptId(draft.inboundReceiptId());
        activity.setReceiptNumber(draft.receiptNumber());
        activity.setMessage(draft.message());
        return workActivityRepository.save(activity);
    }

    public record WorkActivityDraft(
            Long employeeId,
            WorkActivityType type,
            DepartmentName departmentName,
            LocalDateTime occurredAt,
            int quantity,
            Long productId,
            String productSku,
            Long locationId,
            String locationCode,
            Long outboundOrderId,
            String orderNumber,
            Long inboundReceiptId,
            String receiptNumber,
            String message
    ) {
        public static WorkActivityDraft pickStarted(Long employeeId, Long orderId, String orderNumber) {
            return new WorkActivityDraft(
                    employeeId, WorkActivityType.PICK_STARTED, DepartmentName.PICKING,
                    null, 0, null, null, null, null, orderId, orderNumber, null, null,
                    "Started picking order " + orderNumber);
        }

        public static WorkActivityDraft pickItem(
                Long employeeId, Long orderId, String orderNumber,
                Long productId, String sku, Long locationId, String locationCode, int qty
        ) {
            return new WorkActivityDraft(
                    employeeId, WorkActivityType.PICK_ITEM, DepartmentName.PICKING,
                    null, qty, productId, sku, locationId, locationCode, orderId, orderNumber, null, null,
                    "Picked " + qty + " × " + sku + " from " + locationCode);
        }

        public static WorkActivityDraft pickCompleted(Long employeeId, Long orderId, String orderNumber, int totalQty) {
            return new WorkActivityDraft(
                    employeeId, WorkActivityType.PICK_COMPLETED, DepartmentName.PICKING,
                    null, totalQty, null, null, null, null, orderId, orderNumber, null, null,
                    "Finished picking order " + orderNumber);
        }

        public static WorkActivityDraft packStarted(Long employeeId, Long orderId, String orderNumber) {
            return new WorkActivityDraft(
                    employeeId, WorkActivityType.PACK_STARTED, DepartmentName.PACKING,
                    null, 0, null, null, null, null, orderId, orderNumber, null, null,
                    "Started packing order " + orderNumber);
        }

        public static WorkActivityDraft packProgress(Long employeeId, Long orderId, String orderNumber, int qty) {
            return new WorkActivityDraft(
                    employeeId, WorkActivityType.PACK_PROGRESS, DepartmentName.PACKING,
                    null, qty, null, null, null, null, orderId, orderNumber, null, null,
                    "Packed " + qty + " items for order " + orderNumber);
        }

        public static WorkActivityDraft packCompleted(Long employeeId, Long orderId, String orderNumber, int totalQty) {
            return new WorkActivityDraft(
                    employeeId, WorkActivityType.PACK_COMPLETED, DepartmentName.PACKING,
                    null, totalQty, null, null, null, null, orderId, orderNumber, null, null,
                    "Finished packing order " + orderNumber);
        }

        public static WorkActivityDraft putawayStarted(Long employeeId, Long receiptId, String receiptNumber) {
            return new WorkActivityDraft(
                    employeeId, WorkActivityType.PUTAWAY_STARTED, DepartmentName.PUTAWAY,
                    null, 0, null, null, null, null, null, null, receiptId, receiptNumber,
                    "Started putaway for receipt " + receiptNumber);
        }

        public static WorkActivityDraft putawayItem(
                Long employeeId, Long receiptId, String receiptNumber,
                Long productId, String sku, Long locationId, String locationCode, int qty
        ) {
            return new WorkActivityDraft(
                    employeeId, WorkActivityType.PUTAWAY_ITEM, DepartmentName.PUTAWAY,
                    null, qty, productId, sku, locationId, locationCode, null, null, receiptId, receiptNumber,
                    "Put away " + qty + " × " + sku + " to " + locationCode);
        }

        public static WorkActivityDraft putawayCompleted(
                Long employeeId, Long receiptId, String receiptNumber, int totalQty
        ) {
            return new WorkActivityDraft(
                    employeeId, WorkActivityType.PUTAWAY_COMPLETED, DepartmentName.PUTAWAY,
                    null, totalQty, null, null, null, null, null, null, receiptId, receiptNumber,
                    "Finished putaway for receipt " + receiptNumber);
        }
    }
}
