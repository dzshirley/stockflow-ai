package com.stockflow.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

class RequestObservabilityFilterTest {

    @Test
    void preservesSafeRequestIdAndRecordsLowCardinalityRequestMetric() throws Exception {
        var meters = new SimpleMeterRegistry();
        var filter = new RequestObservabilityFilter(meters);
        var request = new MockHttpServletRequest("GET", "/api/health");
        request.addHeader(RequestObservabilityFilter.REQUEST_ID_HEADER, "trace-123");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/health");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestObservabilityFilter.REQUEST_ID_HEADER)).isEqualTo("trace-123");
        assertThat(meters.find("stockflow.http.request.duration")
                .tag("method", "GET").tag("route", "/api/health").tag("status", "200")
                .timer().count()).isEqualTo(1);
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void replacesUnsafeRequestIdInsteadOfReflectingIt() throws Exception {
        var filter = new RequestObservabilityFilter(new SimpleMeterRegistry());
        var request = new MockHttpServletRequest("GET", "/api/health");
        request.addHeader(RequestObservabilityFilter.REQUEST_ID_HEADER, "unsafe\nvalue");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(UUID.fromString(response.getHeader(RequestObservabilityFilter.REQUEST_ID_HEADER))).isNotNull();
    }
}
