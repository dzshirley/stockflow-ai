package com.stockflow.order;

import java.time.Clock;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OrderExpirationService {

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final Clock clock;

    public OrderExpirationService(OrderRepository orderRepository, OrderService orderService, Clock clock) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${stockflow.orders.expiration-delay:PT1M}")
    public void expireDueOrders() {
        var dueOrders = orderRepository.findTop100ByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
                OrderStatus.PENDING_PAYMENT, Instant.now(clock));
        for (var order : dueOrders) {
            orderService.expire(order.getStoreId(), order.getId(), order.getCreatedBy());
        }
    }
}
