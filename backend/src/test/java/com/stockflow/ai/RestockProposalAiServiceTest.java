package com.stockflow.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RestockProposalAiServiceTest {
    private static final UUID PRODUCT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final RestockProposalAiService.Candidate CANDIDATE =
            new RestockProposalAiService.Candidate(
                    PRODUCT, "SKU-1", "LOW_STOCK", 1, 0, 5, new BigDecimal("1.2"), new BigDecimal("0.8"));

    @Test
    void acceptsOnlyIdsFromTheBoundedCandidateSet() {
        var service = service("{\"selectedProductIds\":[\"" + PRODUCT + "\"]}");
        assertThat(service.select("restock risk", List.of(CANDIDATE)).productIds()).containsExactly(PRODUCT);
    }

    @Test
    void carriesProviderReportedUsageAndEstimatedCostIntoTheSelection() {
        var usage = new AiModels.ProviderUsage(12, 4, 16, new BigDecimal("0.00000420"), "prices-v1");
        var gateway = new StubGateway("{\"selectedProductIds\":[\"" + PRODUCT + "\"]}", usage);

        var selection = new RestockProposalAiService(gateway, new ObjectMapper(), new SimpleMeterRegistry())
                .select("restock risk", List.of(CANDIDATE));

        assertThat(selection.inputTokens()).isEqualTo(12);
        assertThat(selection.outputTokens()).isEqualTo(4);
        assertThat(selection.totalTokens()).isEqualTo(16);
        assertThat(selection.estimatedCostUsd()).isEqualByComparingTo("0.00000420");
        assertThat(selection.pricingVersion()).isEqualTo("prices-v1");
        assertThat(selection.latencyMs()).isNotNegative();
    }

    @Test
    void rejectsMalformedOrUnauthorizedProviderSelection() {
        var outside = UUID.fromString("22222222-2222-2222-2222-222222222222");
        assertThatThrownBy(() -> service("{\"selectedProductIds\":[\"" + outside + "\"]}")
                .select("restock risk", List.of(CANDIDATE)))
                .isInstanceOf(AiProviderException.class)
                .extracting(error -> ((AiProviderException) error).code()).isEqualTo("AI_PROVIDER_MALFORMED_RESPONSE");
        assertThatThrownBy(() -> service("not-json").select("restock risk", List.of(CANDIDATE)))
                .isInstanceOf(AiProviderException.class);
    }

    @Test
    void propagatesProviderOutageWithoutAnyOperationalWriteCapability() {
        var gateway = new StubGateway(null) {
            @Override public AiModels.ProviderResult selectRestock(AiModels.RestockSelectionRequest request) {
                throw new AiProviderException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "AI_PROVIDER_UNAVAILABLE", "Provider unavailable", null);
            }
        };
        var service = new RestockProposalAiService(gateway, new ObjectMapper(), new SimpleMeterRegistry());
        assertThatThrownBy(() -> service.select("restock risk", List.of(CANDIDATE)))
                .isInstanceOf(AiProviderException.class)
                .extracting(error -> ((AiProviderException) error).code()).isEqualTo("AI_PROVIDER_UNAVAILABLE");
    }

    private RestockProposalAiService service(String response) {
        return new RestockProposalAiService(new StubGateway(response), new ObjectMapper(), new SimpleMeterRegistry());
    }

    private static class StubGateway implements AiProviderGateway {
        private final String response;
        private final AiModels.ProviderUsage usage;
        StubGateway(String response) { this(response, AiModels.ProviderUsage.unavailable()); }
        StubGateway(String response, AiModels.ProviderUsage usage) { this.response = response; this.usage = usage; }
        @Override public AiModels.ProviderResult selectRestock(AiModels.RestockSelectionRequest request) {
            return new AiModels.ProviderResult("stub", "stub-model", response, usage);
        }
        @Override public AiModels.ProviderResult execute(AiModels.ProviderRequest request) { throw new UnsupportedOperationException(); }
        @Override public AiModels.ProviderResult synthesize(AiModels.BusinessSynthesisRequest request) { throw new UnsupportedOperationException(); }
        @Override public String providerName() { return "stub"; }
        @Override public String configuredModel() { return "stub-model"; }
    }
}
