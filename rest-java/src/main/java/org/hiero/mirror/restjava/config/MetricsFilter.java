// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.config;

import static org.springframework.http.HttpHeaders.CONTENT_LENGTH;
import static org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Meter.MeterProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.inject.Named;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.CustomLog;
import org.apache.catalina.connector.ResponseFacade;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

@CustomLog
@Named
class MetricsFilter extends OncePerRequestFilter {

    static final String REQUEST_BYTES = "hiero.mirror.restjava.request.bytes";
    static final String RESPONSE_BYTES = "hiero.mirror.restjava.response.bytes";

    private static final String METHOD = "method";
    private static final String URI = "uri";

    private final MeterProvider<DistributionSummary> requestBytesProvider;
    private final MeterProvider<DistributionSummary> responseBytesProvider;

    MetricsFilter(MeterRegistry meterRegistry) {
        this.requestBytesProvider = DistributionSummary.builder(REQUEST_BYTES)
                .baseUnit("bytes")
                .description("The size of the request in bytes")
                .withRegistry(meterRegistry);
        this.responseBytesProvider = DistributionSummary.builder(RESPONSE_BYTES)
                .baseUnit("bytes")
                .description("The size of the response in bytes")
                .withRegistry(meterRegistry);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            filterChain.doFilter(request, response);
        } finally {
            recordMetrics(request, response);
        }
    }

    private void recordMetrics(HttpServletRequest request, ServletResponse response) {
        if (request.getAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE) instanceof String uri) {
            var tags = Tags.of(METHOD, request.getMethod(), URI, uri);

            var contentLengthHeader = request.getHeader(CONTENT_LENGTH);
            if (contentLengthHeader != null) {
                long contentLength = Math.max(0L, NumberUtils.toLong(contentLengthHeader));
                requestBytesProvider.withTags(tags).record(contentLength);
            }

            var responseFacade = WebUtils.getNativeResponse(response, ResponseFacade.class);
            if (responseFacade != null) {
                var responseSize = responseFacade.getContentWritten();
                responseBytesProvider.withTags(tags).record(responseSize);
            }
        }
    }
}
