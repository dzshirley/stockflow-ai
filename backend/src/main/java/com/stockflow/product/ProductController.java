package com.stockflow.product;

import com.stockflow.store.StoreContextProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final StoreContextProvider contextProvider;

    public ProductController(ProductService productService, StoreContextProvider contextProvider) {
        this.productService = productService;
        this.contextProvider = contextProvider;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ProductModels.Response create(@Valid @RequestBody ProductModels.CreateRequest request) {
        return productService.create(contextProvider.current(), request);
    }

    @GetMapping("/{id}")
    ProductModels.Response get(@PathVariable UUID id) {
        return productService.get(contextProvider.current(), id);
    }

    @GetMapping
    ProductModels.PageResponse search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "ACTIVE") ProductStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "ASC") Sort.Direction direction) {
        return productService.search(
                contextProvider.current(), q, category, status, page, size, sort, direction);
    }

    @PatchMapping("/{id}")
    ProductModels.Response update(@PathVariable UUID id, @Valid @RequestBody ProductModels.UpdateRequest request) {
        return productService.update(contextProvider.current(), id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archive(@PathVariable UUID id) {
        productService.archive(contextProvider.current(), id);
    }
}
