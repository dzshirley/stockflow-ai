package com.stockflow.analytics;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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

@WebMvcTest(AnalyticsController.class)
@Import(SecurityConfiguration.class)
class AnalyticsSecurityMvcTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean AnalyticsService analyticsService;
    @MockitoBean StoreContextProvider contextProvider;

    @Test
    void rejectsAnonymousAnalyticsAccess() throws Exception {
        mockMvc.perform(get("/api/analytics/overview")).andExpect(status().isUnauthorized());
    }

    @Test
    void allowsAuthenticatedStoreMemberAndValidatesLimits() throws Exception {
        var context = new StoreContext(UUID.randomUUID(), UUID.randomUUID(), StoreRole.STAFF);
        org.mockito.Mockito.when(contextProvider.current()).thenReturn(context);

        mockMvc.perform(get("/api/analytics/overview").with(user("staff").roles("STAFF")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/analytics/products/top")
                        .param("limit", "51")
                        .with(user("staff").roles("STAFF")))
                .andExpect(status().isBadRequest());
    }
}
