package com.stockflow.analysis;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stockflow.auth.SecurityConfiguration;
import com.stockflow.store.StoreContext;
import com.stockflow.store.StoreContextProvider;
import com.stockflow.store.StoreRole;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BusinessAnalysisController.class)
@Import(SecurityConfiguration.class)
class BusinessAnalysisSecurityMvcTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean BusinessReportService reports;
    @MockitoBean StoreContextProvider contexts;

    @Test
    void rejectsAnonymousReportAccess() throws Exception {
        mockMvc.perform(get("/api/business-analysis/reports"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requiresCsrfAndIdempotencyForCreation() throws Exception {
        mockMvc.perform(post("/api/business-analysis/reports")
                        .with(user("staff").roles("STAFF"))
                        .header("Idempotency-Key", "report-key")
                        .contentType("application/json")
                        .content("{\"question\":\"Why did revenue decline?\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/business-analysis/reports")
                        .with(user("staff").roles("STAFF"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"question\":\"Why did revenue decline?\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validatesRequestBeforeBusinessAnalysis() throws Exception {
        mockMvc.perform(post("/api/business-analysis/reports")
                        .with(user("staff").roles("STAFF"))
                        .with(csrf())
                        .header("Idempotency-Key", "report-key")
                        .contentType("application/json")
                        .content("{\"question\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void derivesListScopeFromServerContext() throws Exception {
        var context = new StoreContext(UUID.randomUUID(), UUID.randomUUID(), StoreRole.STAFF);
        when(contexts.current()).thenReturn(context);
        when(reports.list(eq(context), any(), any(), eq(20)))
                .thenReturn(new BusinessAnalysisModels.ReportList(List.of()));

        mockMvc.perform(get("/api/business-analysis/reports").with(user("staff").roles("STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }
}
