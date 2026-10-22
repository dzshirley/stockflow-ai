package com.stockflow.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class BusinessAnalysisAiServiceTest {

    @Test
    void acceptsOnlyAllowListedBoundedSelectionCodes() {
        var provider = mock(AiProviderGateway.class);
        when(provider.synthesize(org.mockito.ArgumentMatchers.any())).thenReturn(new AiModels.ProviderResult(
                "mock", "deterministic-v1",
                "{\"hypothesisCodes\":[\"PRODUCT_MIX_MAY_CONTRIBUTE\"],"
                        + "\"recommendationCodes\":[\"REVIEW_REVENUE_DRIVERS\"]}",
                AiModels.ProviderUsage.unavailable()));
        var service = new BusinessAnalysisAiService(
                provider, new ObjectMapper().findAndRegisterModules(), new SimpleMeterRegistry());

        var result = service.synthesize("{\"verified\":true}");

        assertThat(result.hypothesisCodes())
                .containsExactly(BusinessAnalysisAiService.HypothesisCode.PRODUCT_MIX_MAY_CONTRIBUTE);
        assertThat(result.recommendationCodes())
                .containsExactly(BusinessAnalysisAiService.RecommendationCode.REVIEW_REVENUE_DRIVERS);
    }

    @Test
    void rejectsMalformedAndInventedCodes() {
        var provider = mock(AiProviderGateway.class);
        when(provider.providerName()).thenReturn("openai");
        when(provider.synthesize(org.mockito.ArgumentMatchers.any())).thenReturn(new AiModels.ProviderResult(
                "openai", "test", "{\"hypothesisCodes\":[\"PROVEN_CAUSATION\"],"
                        + "\"recommendationCodes\":[]}", AiModels.ProviderUsage.unavailable()));
        var service = new BusinessAnalysisAiService(
                provider, new ObjectMapper().findAndRegisterModules(), new SimpleMeterRegistry());

        assertThatThrownBy(() -> service.synthesize("{}"))
                .isInstanceOf(AiProviderException.class)
                .extracting(exception -> ((AiProviderException) exception).code())
                .isEqualTo("AI_PROVIDER_MALFORMED_RESPONSE");
    }

    @Test
    void capsDuplicateProviderSelections() {
        var provider = mock(AiProviderGateway.class);
        when(provider.synthesize(org.mockito.ArgumentMatchers.any())).thenReturn(new AiModels.ProviderResult(
                "mock", "test", "{\"hypothesisCodes\":[],\"recommendationCodes\":["
                        + "\"REVIEW_REVENUE_DRIVERS\",\"REVIEW_REVENUE_DRIVERS\"]}",
                AiModels.ProviderUsage.unavailable()));
        var service = new BusinessAnalysisAiService(
                provider, new ObjectMapper().findAndRegisterModules(), new SimpleMeterRegistry());

        assertThat(service.synthesize("{}").recommendationCodes())
                .isEqualTo(List.of(BusinessAnalysisAiService.RecommendationCode.REVIEW_REVENUE_DRIVERS));
    }
}
