package com.stockflow.common;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "stockflow.scheduling.enabled", matchIfMissing = true)
public class SchedulingConfiguration {
}
