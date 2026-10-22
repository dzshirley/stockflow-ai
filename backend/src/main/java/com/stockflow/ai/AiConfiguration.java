package com.stockflow.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@EnableConfigurationProperties({
        AiConfiguration.AiProperties.class,
        AiConfiguration.AiPricingProperties.class,
        AiConfiguration.AiResilienceProperties.class
})
class AiConfiguration {

    @Bean
    ObjectMapper aiObjectMapper() {
        // Spring Boot 4 uses Jackson 3 for HTTP. Spring AI 2 still uses Jackson 2,
        // so AI persistence and tool metadata deliberately own this mapper.
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    OpenAiHttpClientBuilderCustomizer openAiTimeoutCustomizer(
            @Value("${spring.ai.openai.timeout:20s}") Duration timeout) {
        int timeoutMillis = Math.toIntExact(Math.max(1, Math.min(Integer.MAX_VALUE, timeout.toMillis())));
        return builder -> builder
                .timeout(com.openai.core.Timeout.builder()
                        .connect(timeout)
                        .read(timeout)
                        .write(timeout)
                        .request(timeout)
                        .build())
                .interceptor(chain -> chain
                        .withConnectTimeout(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .withReadTimeout(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .withWriteTimeout(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .proceed(chain.request()));
    }

    @Validated
    @ConfigurationProperties(prefix = "stockflow.ai")
    record AiProperties(
            @DefaultValue("mock") @Pattern(regexp = "mock|openai") String provider,
            @DefaultValue("1000") @Min(100) @Max(4000) int maxQuestionLength,
            @DefaultValue("12") @Min(2) @Max(30) int maxHistoryMessages,
            @DefaultValue("6") @Min(1) @Max(12) int maxToolCalls,
            @DefaultValue("50000") @Min(500) @Max(100000) int maxResponseCharacters) {
    }

    @Validated
    @ConfigurationProperties(prefix = "stockflow.ai.pricing")
    record AiPricingProperties(
            @DefaultValue("2026-10-22") @NotBlank String version,
            @DefaultValue("gpt-4o-mini") @NotBlank String model,
            @DefaultValue("0.15") @DecimalMin("0.0") BigDecimal inputUsdPerMillion,
            @DefaultValue("0.60") @DecimalMin("0.0") BigDecimal outputUsdPerMillion) {
    }

    @Validated
    @ConfigurationProperties(prefix = "stockflow.ai.resilience")
    record AiResilienceProperties(
            @DefaultValue("2") @Min(1) @Max(4) int maxAttempts,
            @DefaultValue("200ms") Duration initialBackoff,
            @DefaultValue("2") @Min(1) @Max(10) int backoffMultiplier,
            @DefaultValue("1s") Duration maxBackoff) {

        AiResilienceProperties {
            if (initialBackoff.isNegative() || maxBackoff.isNegative()
                    || maxBackoff.compareTo(initialBackoff) < 0) {
                throw new IllegalArgumentException("AI retry backoff bounds are invalid");
            }
        }
    }
}
