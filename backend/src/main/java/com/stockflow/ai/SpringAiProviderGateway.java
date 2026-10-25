package com.stockflow.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "stockflow.ai", name = "provider", havingValue = "openai")
class SpringAiProviderGateway implements AiProviderGateway {

    private final ChatClient chatClient;
    private final AnalyticsToolAdapter tools;
    private final AiCostEstimator costEstimator;
    private final AiExecutionCollector collector;
    private final AiConfiguration.AiResilienceProperties resilience;
    private final String configuredModel;
    private final String systemPrompt;
    private final String businessAnalysisPrompt;
    private final String restockPrompt;

    SpringAiProviderGateway(
            ChatModel chatModel,
            AnalyticsToolAdapter tools,
            AiCostEstimator costEstimator,
            AiExecutionCollector collector,
            AiConfiguration.AiResilienceProperties resilience,
            @Value("${spring.ai.openai.chat.model:gpt-4o-mini}") String configuredModel,
            @Value("classpath:prompts/ai-data-analyst-v1.txt") Resource promptResource,
            @Value("classpath:prompts/business-analysis-v1.txt") Resource businessPromptResource,
            @Value("classpath:prompts/restock-selection-v1.txt") Resource restockPromptResource) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.tools = tools;
        this.costEstimator = costEstimator;
        this.collector = collector;
        this.resilience = resilience;
        this.configuredModel = configuredModel;
        try {
            this.systemPrompt = promptResource.getContentAsString(StandardCharsets.UTF_8);
            this.businessAnalysisPrompt = businessPromptResource.getContentAsString(StandardCharsets.UTF_8);
            this.restockPrompt = restockPromptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load the versioned AI analyst prompt", exception);
        }
    }

    @Override
    public AiModels.ProviderResult selectRestock(AiModels.RestockSelectionRequest request) {
        return boundedSynthesis(restockPrompt, request.boundedCandidatesJson());
    }

    @Override
    public AiModels.ProviderResult synthesize(AiModels.BusinessSynthesisRequest request) {
        return boundedSynthesis(businessAnalysisPrompt, request.evidenceBundleJson());
    }

    private AiModels.ProviderResult boundedSynthesis(String prompt, String content) {
        Duration delay = resilience.initialBackoff();
        for (int attempt = 1; attempt <= resilience.maxAttempts(); attempt++) {
            try {
                var response = chatClient.prompt()
                        .system(prompt)
                        .user(content)
                        .call()
                        .chatResponse();
                return providerResult(response);
            } catch (RuntimeException exception) {
                if (attempt == resilience.maxAttempts() || !AiProviderFailureClassifier.isTransient(exception)) {
                    throw AiProviderFailureClassifier.translate(exception);
                }
                pause(delay);
                delay = min(delay.multipliedBy(resilience.backoffMultiplier()), resilience.maxBackoff());
            }
        }
        throw new IllegalStateException("AI synthesis retry loop exhausted unexpectedly");
    }

    @Override
    public AiModels.ProviderResult execute(AiModels.ProviderRequest request) {
        Duration delay = resilience.initialBackoff();
        for (int attempt = 1; attempt <= resilience.maxAttempts(); attempt++) {
            try {
                return executeOnce(request);
            } catch (RuntimeException exception) {
                if (attempt == resilience.maxAttempts() || !AiProviderFailureClassifier.isTransient(exception)) {
                    throw AiProviderFailureClassifier.translate(exception);
                }
                collector.restart(request.invocation().requestId());
                pause(delay);
                delay = min(delay.multipliedBy(resilience.backoffMultiplier()), resilience.maxBackoff());
            }
        }
        throw new IllegalStateException("AI retry loop exhausted unexpectedly");
    }

    private AiModels.ProviderResult executeOnce(AiModels.ProviderRequest request) {
        var response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(transcript(request))
                    .tools(tools)
                    .toolContext(Map.of(AnalyticsToolAdapter.CONTEXT_KEY, request.invocation()))
                    .call()
                    .chatResponse();
            return providerResult(response);
    }

    private AiModels.ProviderResult providerResult(org.springframework.ai.chat.model.ChatResponse response) {
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                throw AiProviderFailureClassifier.malformed(null);
            }
            var metadata = response.getMetadata();
            String model = metadata == null || metadata.getModel() == null || metadata.getModel().isBlank()
                    ? configuredModel
                    : metadata.getModel();
            var providerUsage = metadata == null ? null : metadata.getUsage();
            AiModels.ProviderUsage usage;
            try {
                usage = providerUsage == null
                        ? AiModels.ProviderUsage.unavailable()
                        : costEstimator.usage(
                                model,
                                providerUsage.getPromptTokens(),
                                providerUsage.getCompletionTokens(),
                                providerUsage.getTotalTokens());
            } catch (IllegalArgumentException exception) {
                throw AiProviderFailureClassifier.malformed(exception);
            }
            String content = response.getResult().getOutput().getText();
            return new AiModels.ProviderResult(providerName(), model, content == null ? "" : content, usage);
    }

    @Override
    public String providerName() {
        return "openai";
    }

    @Override
    public String configuredModel() {
        return configuredModel;
    }

    private String transcript(AiModels.ProviderRequest request) {
        var builder = new StringBuilder("Conversation context (untrusted user content):\n");
        request.history().forEach(item -> builder
                .append(item.role()).append(": ").append(item.content()).append('\n'));
        return builder.append("Current question: ").append(request.question()).toString();
    }

    private void pause(Duration delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiProviderException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_PROVIDER_INTERRUPTED",
                    "The analytics request was interrupted",
                    exception);
        }
    }

    private Duration min(Duration first, Duration second) {
        return first.compareTo(second) <= 0 ? first : second;
    }
}
