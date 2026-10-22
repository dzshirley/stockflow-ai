package com.stockflow.common.health;

import java.time.Clock;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/health", produces = MediaType.APPLICATION_JSON_VALUE)
public class HealthController {

    private final Clock clock;

    public HealthController(Clock clock) {
        this.clock = clock;
    }

    @GetMapping
    public HealthResponse health() {
        return new HealthResponse("UP", "stockflow-api", Instant.now(clock));
    }
}
