package com.stockflow.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.stockflow.auth.AuthModels;
import com.stockflow.auth.AuthenticationService;
import com.stockflow.inventory.InventoryService;
import com.stockflow.product.ProductModels;
import com.stockflow.product.ProductService;
import com.stockflow.store.StoreContext;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ai_outage;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class AiOutageIsolationIT {

    @Autowired AuthenticationService authenticationService;
    @Autowired ProductService productService;
    @Autowired InventoryService inventoryService;
    @Autowired AiAnalyticsService aiAnalyticsService;
    @Autowired JdbcTemplate jdbcTemplate;
    @MockitoBean AiProviderGateway provider;

    @Test
    void commerceRemainsAvailableAndUnchangedDuringProviderOutage() {
        when(provider.providerName()).thenReturn("openai");
        when(provider.configuredModel()).thenReturn("gpt-4o-mini");
        when(provider.execute(any())).thenThrow(new AiProviderException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "AI_PROVIDER_UNAVAILABLE",
                "The analytics provider is temporarily unavailable",
                new java.io.IOException("connection reset")));
        var principal = authenticationService.register(new AuthModels.RegisterRequest(
                "outage@example.com", "a-secure-password", "Outage Owner", "Outage Store",
                "USD", "America/New_York"));
        var context = new StoreContext(principal.userId(), principal.storeId(), principal.role());
        var product = productService.create(context, new ProductModels.CreateRequest(
                "OUTAGE-SKU", "Outage Product", "General", new BigDecimal("10.0000"),
                new BigDecimal("4.0000"), 5L, 1L));

        assertThatThrownBy(() -> aiAnalyticsService.query(
                context, new AiModels.QueryRequest(null, "Show revenue")))
                .isInstanceOf(AiProviderException.class)
                .extracting(exception -> ((AiProviderException) exception).code())
                .isEqualTo("AI_PROVIDER_UNAVAILABLE");

        assertThat(productService.get(context, product.id()).sku()).isEqualTo("OUTAGE-SKU");
        assertThat(inventoryService.get(context, product.id()).availableQuantity()).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_request_telemetry WHERE store_id = ? AND status = 'FAILED' "
                        + "AND error_code = 'AI_PROVIDER_UNAVAILABLE'",
                Long.class, context.storeId())).isEqualTo(1);
    }
}
