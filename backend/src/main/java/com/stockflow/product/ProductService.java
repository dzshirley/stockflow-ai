package com.stockflow.product;

import com.stockflow.common.error.ApiExceptions;
import com.stockflow.store.StoreContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private static final Set<String> SORT_FIELDS =
            Set.of("name", "sku", "category", "price", "createdAt", "updatedAt");

    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ProductService(ProductRepository productRepository, ApplicationEventPublisher eventPublisher, Clock clock) {
        this.productRepository = productRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public ProductModels.Response create(StoreContext context, ProductModels.CreateRequest request) {
        var sku = normalizeSku(request.sku());
        if (productRepository.existsByStoreIdAndSku(context.storeId(), sku)) {
            throw ApiExceptions.conflict("SKU_ALREADY_EXISTS", "SKU already exists in this store");
        }
        var now = Instant.now(clock);
        var product = new Product(
                UUID.randomUUID(),
                context.storeId(),
                sku,
                request.name().trim(),
                request.category().trim(),
                request.price().setScale(4, RoundingMode.UNNECESSARY),
                request.unitCost().setScale(4, RoundingMode.UNNECESSARY),
                now);
        productRepository.save(product);
        eventPublisher.publishEvent(new ProductCreatedEvent(
                context.storeId(), product.getId(), context.userId(), request.initialQuantity(), request.reorderPoint()));
        return ProductModels.Response.from(product);
    }

    @Transactional(readOnly = true)
    public ProductModels.Response get(StoreContext context, UUID id) {
        return ProductModels.Response.from(requireOwnedProduct(context.storeId(), id));
    }

    @Transactional(readOnly = true)
    public ProductModels.PageResponse search(
            StoreContext context,
            String search,
            String category,
            ProductStatus status,
            int page,
            int size,
            String sort,
            Sort.Direction direction) {
        if (!SORT_FIELDS.contains(sort)) {
            throw ApiExceptions.badRequest("INVALID_SORT", "Unsupported product sort field");
        }
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(direction, sort));
        // Bind absent text filters as strings rather than untyped SQL NULL values. PostgreSQL
        // otherwise infers Hibernate's LOWER(:parameter) placeholder as bytea.
        var result = productRepository.search(
                context.storeId(), status, blankToEmpty(category), blankToEmpty(search), pageable);
        return new ProductModels.PageResponse(
                result.getContent().stream().map(ProductModels.Response::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional
    public ProductModels.Response update(StoreContext context, UUID id, ProductModels.UpdateRequest request) {
        var product = requireOwnedProduct(context.storeId(), id);
        String sku = null;
        if (request.sku() != null) {
            sku = normalizeSku(request.sku());
            if (!sku.equals(product.getSku()) && productRepository.existsByStoreIdAndSku(context.storeId(), sku)) {
                throw ApiExceptions.conflict("SKU_ALREADY_EXISTS", "SKU already exists in this store");
            }
        }
        product.update(
                sku,
                trimToNull(request.name()),
                trimToNull(request.category()),
                scale(request.price()),
                scale(request.unitCost()),
                Instant.now(clock));
        return ProductModels.Response.from(product);
    }

    @Transactional
    public void archive(StoreContext context, UUID id) {
        requireOwnedProduct(context.storeId(), id).archive(Instant.now(clock));
    }

    public Product requireActiveProduct(UUID storeId, UUID productId) {
        var product = requireOwnedProduct(storeId, productId);
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw ApiExceptions.conflict("PRODUCT_ARCHIVED", "Archived products cannot be ordered");
        }
        return product;
    }

    public Product requireOwnedProduct(UUID storeId, UUID id) {
        return productRepository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> ApiExceptions.notFound("Product"));
    }

    private String normalizeSku(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToEmpty(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    private String trimToNull(String value) {
        return value == null ? null : value.trim();
    }

    private java.math.BigDecimal scale(java.math.BigDecimal value) {
        return value == null ? null : value.setScale(4, RoundingMode.UNNECESSARY);
    }
}
