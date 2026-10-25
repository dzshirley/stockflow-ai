package com.stockflow.inventory;

import com.stockflow.common.error.ApiExceptions;
import com.stockflow.product.ProductCreatedEvent;
import com.stockflow.product.ProductService;
import com.stockflow.store.StoreContext;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryAdjustmentRepository adjustmentRepository;
    private final ProductService productService;
    private final Clock clock;

    public InventoryService(
            InventoryRepository inventoryRepository,
            InventoryAdjustmentRepository adjustmentRepository,
            ProductService productService,
            Clock clock) {
        this.inventoryRepository = inventoryRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.productService = productService;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void initialize(ProductCreatedEvent event) {
        var now = Instant.now(clock);
        inventoryRepository.save(new InventoryBalance(
                event.productId(), event.storeId(), event.initialQuantity(), event.reorderPoint(), now));
        if (event.initialQuantity() > 0) {
            record(event.storeId(), event.productId(), event.initialQuantity(), 0, "INITIAL_STOCK", event.actorUserId(), now);
        }
    }

    @Transactional(readOnly = true)
    public InventoryModels.BalanceResponse get(StoreContext context, UUID productId) {
        return response(context.storeId(), requireBalance(context.storeId(), productId));
    }

    @Transactional(readOnly = true)
    public InventoryModels.ListResponse list(StoreContext context, boolean lowStockOnly) {
        List<InventoryBalance> balances = lowStockOnly
                ? inventoryRepository.findLowStock(context.storeId())
                : inventoryRepository.findByStoreIdOrderByProductId(context.storeId());
        return new InventoryModels.ListResponse(
                balances.stream().map(balance -> response(context.storeId(), balance)).toList());
    }

    @Transactional
    public InventoryModels.BalanceResponse adjust(
            StoreContext context, UUID productId, InventoryModels.AdjustmentRequest request) {
        productService.requireOwnedProduct(context.storeId(), productId);
        if (request.availableDelta() == 0 && request.reorderPoint() == null) {
            throw ApiExceptions.badRequest(
                    "EMPTY_INVENTORY_ADJUSTMENT", "Provide a non-zero availableDelta or a reorderPoint");
        }
        if (request.availableDelta() != 0 && (request.reason() == null || request.reason().isBlank())) {
            throw ApiExceptions.badRequest("ADJUSTMENT_REASON_REQUIRED", "reason is required for stock changes");
        }
        var now = Instant.now(clock);
        if (request.availableDelta() != 0) {
            if (inventoryRepository.adjustAvailable(
                            context.storeId(), productId, request.availableDelta(), now)
                    != 1) {
                throw ApiExceptions.conflict(
                        "INSUFFICIENT_INVENTORY", "Adjustment would make available inventory negative");
            }
            record(
                    context.storeId(),
                    productId,
                    request.availableDelta(),
                    0,
                    request.reason().trim(),
                    context.userId(),
                    now);
        }
        if (request.reorderPoint() != null
                && inventoryRepository.updateReorderPoint(
                                context.storeId(), productId, request.reorderPoint(), now)
                        != 1) {
            throw ApiExceptions.notFound("Inventory");
        }
        return response(context.storeId(), requireBalance(context.storeId(), productId));
    }

    @Transactional(readOnly = true)
    public InventoryModels.AuditResponse audit(StoreContext context, UUID productId, int page, int size) {
        productService.requireActiveProduct(context.storeId(), productId);
        var result = adjustmentRepository.findByStoreIdAndProductIdOrderByCreatedAtDesc(
                context.storeId(), productId, PageRequest.of(page, Math.min(size, 100)));
        return new InventoryModels.AuditResponse(
                result.getContent().stream().map(InventoryModels.AdjustmentResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements());
    }

    public void reserve(UUID storeId, UUID productId, long quantity, UUID actorUserId, String reason) {
        var now = Instant.now(clock);
        if (inventoryRepository.reserve(storeId, productId, quantity, now) != 1) {
            throw ApiExceptions.conflict("INSUFFICIENT_INVENTORY", "Insufficient inventory for product " + productId);
        }
        record(storeId, productId, -quantity, quantity, reason, actorUserId, now);
    }

    public void release(UUID storeId, UUID productId, long quantity, UUID actorUserId, String reason) {
        var now = Instant.now(clock);
        if (inventoryRepository.release(storeId, productId, quantity, now) != 1) {
            throw ApiExceptions.conflict("INVENTORY_STATE_CONFLICT", "Reserved inventory could not be released");
        }
        record(storeId, productId, quantity, -quantity, reason, actorUserId, now);
    }

    public void consume(UUID storeId, UUID productId, long quantity, UUID actorUserId, String reason) {
        var now = Instant.now(clock);
        if (inventoryRepository.consumeReservation(storeId, productId, quantity, now) != 1) {
            throw ApiExceptions.conflict("INVENTORY_STATE_CONFLICT", "Reserved inventory could not be consumed");
        }
        record(storeId, productId, 0, -quantity, reason, actorUserId, now);
    }

    @Transactional(readOnly = true)
    public InventorySnapshot snapshot(UUID storeId, UUID productId) {
        var balance = requireBalance(storeId, productId);
        return new InventorySnapshot(
                balance.getProductId(), balance.getAvailableQuantity(), balance.getReservedQuantity(),
                balance.getReorderPoint(), balance.getVersion(), balance.getUpdatedAt());
    }

    /** Deterministic receipt command used only by an already authorized operational workflow. */
    public ReceiptResult receiveRestock(
            UUID storeId,
            UUID productId,
            long quantity,
            long expectedVersion,
            long expectedAvailable,
            long expectedReserved,
            long expectedReorderPoint,
            UUID actorUserId,
            UUID commandId) {
        if (quantity <= 0) throw ApiExceptions.badRequest("INVALID_RESTOCK_QUANTITY", "Quantity must be positive");
        var existing = adjustmentRepository.findByOperationCommandId(commandId);
        if (existing.isPresent()) {
            var adjustment = existing.get();
            if (!adjustment.getProductId().equals(productId) || adjustment.getAvailableDelta() != quantity) {
                throw ApiExceptions.conflict("RESTOCK_COMMAND_CONFLICT", "Command ID was used for another receipt");
            }
            return new ReceiptResult(adjustment.getId(), snapshot(storeId, productId));
        }
        productService.requireActiveProduct(storeId, productId);
        var now = Instant.now(clock);
        if (inventoryRepository.receiveRestock(
                storeId, productId, quantity, expectedVersion, expectedAvailable,
                expectedReserved, expectedReorderPoint, now) != 1) {
            throw ApiExceptions.conflict("RESTOCK_INVENTORY_STALE", "Inventory changed after this proposal was created");
        }
        var adjustment = adjustmentRepository.save(new InventoryAdjustment(
                UUID.randomUUID(), storeId, productId, quantity, 0,
                "RESTOCK_RECEIPT", actorUserId, commandId, now));
        return new ReceiptResult(adjustment.getId(), snapshot(storeId, productId));
    }

    public record InventorySnapshot(
            UUID productId, long availableQuantity, long reservedQuantity, long reorderPoint,
            long version, Instant updatedAt) {}

    public record ReceiptResult(UUID adjustmentId, InventorySnapshot inventory) {}

    private InventoryModels.BalanceResponse response(UUID storeId, InventoryBalance balance) {
        var product = productService.requireOwnedProduct(storeId, balance.getProductId());
        return new InventoryModels.BalanceResponse(
                balance.getProductId(),
                product.getSku(),
                product.getName(),
                balance.getAvailableQuantity(),
                balance.getReservedQuantity(),
                balance.getReorderPoint(),
                balance.getAvailableQuantity() <= balance.getReorderPoint(),
                balance.getVersion(),
                balance.getUpdatedAt());
    }

    private InventoryBalance requireBalance(UUID storeId, UUID productId) {
        return inventoryRepository.findByProductIdAndStoreId(productId, storeId)
                .orElseThrow(() -> ApiExceptions.notFound("Inventory"));
    }

    private void record(
            UUID storeId,
            UUID productId,
            long availableDelta,
            long reservedDelta,
            String reason,
            UUID actorUserId,
            Instant now) {
        adjustmentRepository.save(new InventoryAdjustment(
                UUID.randomUUID(),
                storeId,
                productId,
                availableDelta,
                reservedDelta,
                reason,
                actorUserId,
                now));
    }
}
