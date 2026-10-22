package com.stockflow.order;

import com.stockflow.common.error.ApiExceptions;
import com.stockflow.inventory.InventoryService;
import com.stockflow.product.Product;
import com.stockflow.product.ProductService;
import com.stockflow.store.StoreContext;
import com.stockflow.store.StoreRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
public class OrderService {

    private static final Duration PAYMENT_WINDOW = Duration.ofMinutes(15);

    private final OrderRepository orderRepository;
    private final OrderItemRepository itemRepository;
    private final OrderIdempotencyGate idempotencyGate;
    private final OutboxEventRepository outboxRepository;
    private final ProductService productService;
    private final InventoryService inventoryService;
    private final StoreRepository storeRepository;
    private final Clock clock;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository itemRepository,
            OrderIdempotencyGate idempotencyGate,
            OutboxEventRepository outboxRepository,
            ProductService productService,
            InventoryService inventoryService,
            StoreRepository storeRepository,
            Clock clock) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.idempotencyGate = idempotencyGate;
        this.outboxRepository = outboxRepository;
        this.productService = productService;
        this.inventoryService = inventoryService;
        this.storeRepository = storeRepository;
        this.clock = clock;
    }

    @Transactional
    public OrderModels.Response create(StoreContext context, String idempotencyKey, OrderModels.CreateRequest request) {
        if (idempotencyKey.isBlank()) {
            throw ApiExceptions.badRequest("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must not be blank");
        }
        var lines = request.items().stream()
                .sorted(java.util.Comparator.comparing(line -> line.productId().toString()))
                .toList();
        var uniqueProducts = new HashSet<UUID>();
        if (lines.stream().anyMatch(line -> !uniqueProducts.add(line.productId()))) {
            throw ApiExceptions.badRequest("DUPLICATE_ORDER_PRODUCT", "Each product may appear only once per order");
        }
        var now = Instant.now(clock);
        var gate = idempotencyGate.acquire(context.storeId(), idempotencyKey, hash(lines), now);
        if (gate.orderId() != null) {
            return response(requireOrder(context.storeId(), gate.orderId()));
        }

        var resolved = lines.stream()
                .map(line -> new ResolvedLine(
                        productService.requireActiveProduct(context.storeId(), line.productId()), line.quantity()))
                .toList();
        var subtotal = resolved.stream()
                .map(line -> line.product().getPrice().multiply(BigDecimal.valueOf(line.quantity())))
                .reduce(BigDecimal.ZERO.setScale(4), BigDecimal::add);
        var store = storeRepository.findById(context.storeId())
                .orElseThrow(() -> ApiExceptions.notFound("Store"));
        var order = new Order(
                UUID.randomUUID(),
                context.storeId(),
                subtotal,
                store.getCurrencyCode(),
                now.plus(PAYMENT_WINDOW),
                context.userId(),
                now);

        for (var line : resolved) {
            inventoryService.reserve(
                    context.storeId(),
                    line.product().getId(),
                    line.quantity(),
                    context.userId(),
                    "ORDER_RESERVED:" + order.getId());
        }

        orderRepository.saveAndFlush(order);
        var items = resolved.stream()
                .map(line -> new OrderItem(
                        UUID.randomUUID(),
                        order.getId(),
                        line.product().getId(),
                        line.product().getSku(),
                        line.product().getName(),
                        line.product().getCategory(),
                        line.quantity(),
                        line.product().getPrice(),
                        line.product().getUnitCost()))
                .toList();
        itemRepository.saveAll(items);
        idempotencyGate.complete(gate.id(), order.getId());
        emit(order, "ORDER_CREATED", now);
        return response(order, items);
    }

    @Transactional(readOnly = true)
    public OrderModels.Response get(StoreContext context, UUID orderId) {
        return response(requireOrder(context.storeId(), orderId));
    }

    @Transactional(readOnly = true)
    public OrderModels.PageResponse list(StoreContext context, int page, int size) {
        var result = orderRepository.findByStoreIdOrderByCreatedAtDesc(
                context.storeId(), PageRequest.of(page, Math.min(size, 100)));
        return new OrderModels.PageResponse(
                result.getContent().stream().map(this::response).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional
    public OrderModels.Response payment(
            StoreContext context, UUID orderId, OrderModels.PaymentOutcome outcome) {
        var order = requireOrderForUpdate(context.storeId(), orderId);
        if ((order.getStatus() == OrderStatus.PAID && outcome == OrderModels.PaymentOutcome.APPROVED)
                || (order.getStatus() == OrderStatus.PAYMENT_FAILED
                        && outcome == OrderModels.PaymentOutcome.DECLINED)) {
            return response(order);
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw ApiExceptions.conflict(
                    "INVALID_ORDER_TRANSITION", "Only pending orders can receive a payment result");
        }
        var items = itemRepository.findByOrderIdOrderById(order.getId());
        var now = Instant.now(clock);
        if (outcome == OrderModels.PaymentOutcome.APPROVED) {
            items.forEach(item -> inventoryService.consume(
                    context.storeId(),
                    item.getProductId(),
                    item.getQuantity(),
                    context.userId(),
                    "ORDER_PAID:" + order.getId()));
            order.markPaid(now);
            emit(order, "ORDER_PAID", now);
        } else {
            items.forEach(item -> inventoryService.release(
                    context.storeId(),
                    item.getProductId(),
                    item.getQuantity(),
                    context.userId(),
                    "PAYMENT_DECLINED:" + order.getId()));
            order.markPaymentFailed(now);
            emit(order, "ORDER_PAYMENT_FAILED", now);
        }
        return response(order, items);
    }

    @Transactional
    public OrderModels.Response cancel(StoreContext context, UUID orderId) {
        var order = requireOrderForUpdate(context.storeId(), orderId);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return response(order);
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw ApiExceptions.conflict("INVALID_ORDER_TRANSITION", "Only pending orders can be cancelled");
        }
        var items = itemRepository.findByOrderIdOrderById(order.getId());
        items.forEach(item -> inventoryService.release(
                context.storeId(),
                item.getProductId(),
                item.getQuantity(),
                context.userId(),
                "ORDER_CANCELLED:" + order.getId()));
        var now = Instant.now(clock);
        order.cancel(now);
        emit(order, "ORDER_CANCELLED", now);
        return response(order, items);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderModels.Response expire(UUID storeId, UUID orderId, UUID actorUserId) {
        var order = requireOrderForUpdate(storeId, orderId);
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return response(order);
        }
        var now = Instant.now(clock);
        if (order.getExpiresAt().isAfter(now)) {
            throw ApiExceptions.conflict("ORDER_NOT_EXPIRED", "The order payment window is still open");
        }
        var items = itemRepository.findByOrderIdOrderById(order.getId());
        items.forEach(item -> inventoryService.release(
                storeId,
                item.getProductId(),
                item.getQuantity(),
                actorUserId,
                "ORDER_EXPIRED:" + order.getId()));
        order.expire(now);
        emit(order, "ORDER_EXPIRED", now);
        return response(order, items);
    }

    private Order requireOrder(UUID storeId, UUID orderId) {
        return orderRepository.findByIdAndStoreId(orderId, storeId)
                .orElseThrow(() -> ApiExceptions.notFound("Order"));
    }

    private Order requireOrderForUpdate(UUID storeId, UUID orderId) {
        return orderRepository.findForUpdate(orderId, storeId)
                .orElseThrow(() -> ApiExceptions.notFound("Order"));
    }

    private OrderModels.Response response(Order order) {
        return response(order, itemRepository.findByOrderIdOrderById(order.getId()));
    }

    private OrderModels.Response response(Order order, List<OrderItem> items) {
        return new OrderModels.Response(
                order.getId(),
                order.getStatus(),
                order.getSubtotal(),
                order.getDiscountTotal(),
                order.getTotal(),
                order.getCurrencyCode(),
                order.getExpiresAt(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                items.stream().map(OrderModels.LineResponse::from).toList());
    }

    private void emit(Order order, String eventType, Instant now) {
        var payload = "{\"orderId\":\"" + order.getId() + "\",\"status\":\"" + order.getStatus() + "\"}";
        outboxRepository.save(new OutboxEvent(
                UUID.randomUUID(), order.getStoreId(), "ORDER", order.getId(), eventType, payload, now));
    }

    private String hash(List<OrderModels.LineRequest> lines) {
        var canonical = lines.stream()
                .map(line -> line.productId() + ":" + line.quantity())
                .collect(java.util.stream.Collectors.joining("|"));
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record ResolvedLine(Product product, long quantity) {
    }
}
