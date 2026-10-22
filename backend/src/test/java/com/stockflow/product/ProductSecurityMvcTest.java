package com.stockflow.product;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stockflow.auth.SecurityConfiguration;
import com.stockflow.store.StoreContext;
import com.stockflow.store.StoreContextProvider;
import com.stockflow.store.StoreRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
@Import(SecurityConfiguration.class)
class ProductSecurityMvcTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ProductService productService;
    @MockitoBean StoreContextProvider contextProvider;

    @Test
    void rejectsAnonymousProductAccess() throws Exception {
        mockMvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
    }

    @Test
    void staffCannotArchiveProducts() throws Exception {
        mockMvc.perform(delete("/api/products/{id}", UUID.randomUUID())
                        .with(user("staff").roles("STAFF"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanArchiveProducts() throws Exception {
        var userId = UUID.randomUUID();
        var storeId = UUID.randomUUID();
        org.mockito.Mockito.when(contextProvider.current())
                .thenReturn(new StoreContext(userId, storeId, StoreRole.ADMIN));

        mockMvc.perform(delete("/api/products/{id}", UUID.randomUUID())
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void rejectsStateChangeWithoutCsrfToken() throws Exception {
        mockMvc.perform(delete("/api/products/{id}", UUID.randomUUID())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }
}
