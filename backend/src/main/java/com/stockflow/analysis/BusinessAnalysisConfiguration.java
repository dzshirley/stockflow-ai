package com.stockflow.analysis;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@EnableConfigurationProperties(BusinessAnalysisConfiguration.Properties.class)
class BusinessAnalysisConfiguration {

    @Validated
    @ConfigurationProperties(prefix = "stockflow.business-analysis")
    record Properties(
            @DefaultValue("90") @Min(1) @Max(3650) int retentionDays,
            @DefaultValue("50") @Min(1) @Max(100) int maxContributionRows,
            @DefaultValue("business-analysis-v1") String promptVersion) {
    }
}
