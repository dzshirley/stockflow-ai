package com.stockflow.ai;

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

@WebMvcTest(AiController.class)
@Import(SecurityConfiguration.class)
class AiSecurityMvcTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean AiAnalyticsService analyticsService;
    @MockitoBean AiConversationService conversationService;
    @MockitoBean StoreContextProvider contextProvider;

    @Test
    void rejectsAnonymousConversationAccess() throws Exception {
        mockMvc.perform(get("/api/ai/conversations")).andExpect(status().isUnauthorized());
    }

    @Test
    void returnsOnlyCurrentUsersConversationList() throws Exception {
        var context = new StoreContext(UUID.randomUUID(), UUID.randomUUID(), StoreRole.STAFF);
        when(contextProvider.current()).thenReturn(context);
        when(conversationService.list(context, 20)).thenReturn(new AiModels.ConversationList(List.of()));

        mockMvc.perform(get("/api/ai/conversations").with(user("staff").roles("STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void validatesQuestionAndCsrfBeforeProviderInvocation() throws Exception {
        mockMvc.perform(post("/api/ai/query")
                        .with(user("staff").roles("STAFF"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"question\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void returnsSanitizedProviderFailureWithoutExposingCredentials() throws Exception {
        var context = new StoreContext(UUID.randomUUID(), UUID.randomUUID(), StoreRole.ADMIN);
        when(contextProvider.current()).thenReturn(context);
        when(analyticsService.query(org.mockito.ArgumentMatchers.eq(context), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new AiProviderException(
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "AI_PROVIDER_UNAVAILABLE",
                        "The analytics provider is temporarily unavailable",
                        new RuntimeException("upstream rejected secret credential")));

        mockMvc.perform(post("/api/ai/query")
                        .with(user("owner").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"question\":\"Show revenue\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").value("The analytics provider is temporarily unavailable"));
    }
}
