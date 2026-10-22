package com.stockflow.inventory;

import com.stockflow.store.StoreContextProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;
    private final StoreContextProvider contextProvider;

    public InventoryController(InventoryService inventoryService, StoreContextProvider contextProvider) {
        this.inventoryService = inventoryService;
        this.contextProvider = contextProvider;
    }

    @GetMapping
    InventoryModels.ListResponse list(@RequestParam(defaultValue = "false") boolean lowStockOnly) {
        return inventoryService.list(contextProvider.current(), lowStockOnly);
    }

    @GetMapping("/{productId}")
    InventoryModels.BalanceResponse get(@PathVariable UUID productId) {
        return inventoryService.get(contextProvider.current(), productId);
    }

    @PostMapping("/{productId}/adjustments")
    InventoryModels.BalanceResponse adjust(
            @PathVariable UUID productId, @Valid @RequestBody InventoryModels.AdjustmentRequest request) {
        return inventoryService.adjust(contextProvider.current(), productId, request);
    }

    @GetMapping("/{productId}/adjustments")
    InventoryModels.AuditResponse audit(
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return inventoryService.audit(contextProvider.current(), productId, page, size);
    }
}
