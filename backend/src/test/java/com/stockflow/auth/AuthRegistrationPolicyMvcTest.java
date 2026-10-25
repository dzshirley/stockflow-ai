package com.stockflow.auth;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import(SecurityConfiguration.class)
@TestPropertySource(properties = "stockflow.security.registration-enabled=false")
class AuthRegistrationPolicyMvcTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean AuthenticationService authenticationService;
    @MockitoBean SessionAuthenticationService sessionAuthenticationService;
    @MockitoBean LoginAttemptGuard loginAttemptGuard;

    @Test
    void rejectsPublicRegistrationWhenDeploymentPolicyDisablesIt() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "email":"demo@example.com",
                                  "password":"a-secure-demo-password",
                                  "displayName":"Demo Owner",
                                  "storeName":"Synthetic Demo",
                                  "currencyCode":"USD",
                                  "timeZone":"America/New_York"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.detail").value("Public registration is disabled"));

        verifyNoInteractions(authenticationService, sessionAuthenticationService);
    }
}
