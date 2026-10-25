package com.stockflow.operations;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@EnableConfigurationProperties(RestockProposalConfiguration.Properties.class)
class RestockProposalConfiguration {

    @Validated
    @ConfigurationProperties(prefix = "stockflow.restock")
    record Properties(
            @DefaultValue("24h") @NotNull Duration freshnessWindow,
            @DefaultValue("2m") @NotNull Duration executionLease,
            @DefaultValue("30s") @NotNull Duration recoveryDelay,
            @DefaultValue("100") @Min(1) @Max(500) int recoveryBatchSize,
            @DefaultValue("30") @Min(1) @Max(365) int targetCoverDays,
            @DefaultValue("1000000") @Min(1) long maxQuantity) {

        Properties {
            if (freshnessWindow.isZero() || freshnessWindow.isNegative()
                    || executionLease.isZero() || executionLease.isNegative()
                    || recoveryDelay.isZero() || recoveryDelay.isNegative()) {
                throw new IllegalArgumentException("Restock durations must be positive");
            }
        }
    }
}
