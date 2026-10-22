package com.stockflow.order;

import com.stockflow.store.StoreContextProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final StoreContextProvider contextProvider;

    public OrderController(OrderService orderService, StoreContextProvider contextProvider) {
        this.orderService = orderService;
        this.contextProvider = contextProvider;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    OrderModels.Response create(
            @RequestHeader("Idempotency-Key") @Size(min = 8, max = 200) String idempotencyKey,
            @Valid @RequestBody OrderModels.CreateRequest request) {
        return orderService.create(contextProvider.current(), idempotencyKey.trim(), request);
    }

    @GetMapping("/{id}")
    OrderModels.Response get(@PathVariable UUID id) {
        return orderService.get(contextProvider.current(), id);
    }

    @GetMapping
    OrderModels.PageResponse list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return orderService.list(contextProvider.current(), page, size);
    }

    @PostMapping("/{id}/payment")
    OrderModels.Response payment(
            @PathVariable UUID id, @Valid @RequestBody OrderModels.PaymentRequest request) {
        return orderService.payment(contextProvider.current(), id, request.outcome());
    }

    @PostMapping("/{id}/cancel")
    OrderModels.Response cancel(@PathVariable UUID id) {
        return orderService.cancel(contextProvider.current(), id);
    }
}
