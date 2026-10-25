package com.stockflow.common.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
public class RequestObservabilityFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Logger LOG = LoggerFactory.getLogger(RequestObservabilityFilter.class);

    private final MeterRegistry meters;

    @Autowired
    public RequestObservabilityFilter(ObjectProvider<MeterRegistry> meters) {
        this(meters.getIfAvailable(SimpleMeterRegistry::new));
    }

    RequestObservabilityFilter(MeterRegistry meters) {
        this.meters = meters;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = requestId(request.getHeader(REQUEST_ID_HEADER));
        response.setHeader(REQUEST_ID_HEADER, requestId);
        long started = System.nanoTime();
        MDC.put("requestId", requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            long durationNanos = System.nanoTime() - started;
            String route = String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
            if (route.equals("null")) route = "UNMATCHED";
            Timer.builder("stockflow.http.request.duration")
                    .description("Application HTTP request duration")
                    .tag("method", request.getMethod())
                    .tag("route", route)
                    .tag("status", Integer.toString(response.getStatus()))
                    .register(meters)
                    .record(durationNanos, TimeUnit.NANOSECONDS);
            LOG.info("http_request_completed method={} route={} status={} durationMs={}",
                    request.getMethod(), route, response.getStatus(), durationNanos / 1_000_000);
            MDC.remove("requestId");
        }
    }

    private String requestId(String candidate) {
        return candidate != null && SAFE_REQUEST_ID.matcher(candidate).matches()
                ? candidate
                : UUID.randomUUID().toString();
    }
}
