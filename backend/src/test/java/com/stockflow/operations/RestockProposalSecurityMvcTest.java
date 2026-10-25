package com.stockflow.operations;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stockflow.auth.SecurityConfiguration;
import com.stockflow.store.StoreContextProvider;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RestockProposalController.class)
@Import(SecurityConfiguration.class)
class RestockProposalSecurityMvcTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean RestockProposalService service;
    @MockitoBean StoreContextProvider contexts;

    @Test
    void rejectsAnonymousProposalAccess() throws Exception {
        mockMvc.perform(get("/api/restock-proposals"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requiresCsrfAndIdempotencyForGeneration() throws Exception {
        String body = """
                {"question":"Prepare a proposal","sourceReportId":"%s","recommendationId":"%s"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/restock-proposals/generate")
                        .with(user("staff").roles("STAFF"))
                        .header("Idempotency-Key", "proposal-key")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/restock-proposals/generate")
                        .with(user("staff").roles("STAFF"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void boundsProposalListSizeBeforeServiceInvocation() throws Exception {
        mockMvc.perform(get("/api/restock-proposals?limit=101")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void requiresReceiptConfirmationPayloadForExecution() throws Exception {
        mockMvc.perform(post("/api/restock-proposals/{id}/execute", UUID.randomUUID())
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType("application/json")
                        .content("{\"received\":null,\"expectedVersion\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
