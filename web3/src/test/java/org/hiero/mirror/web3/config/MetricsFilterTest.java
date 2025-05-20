// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.web3.config.MetricsFilter.REQUEST_BYTES;
import static org.hiero.mirror.web3.config.MetricsFilter.RESPONSE_BYTES;
import static org.mockito.Mockito.doReturn;
import static org.springframework.http.HttpHeaders.CONTENT_LENGTH;
import static org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.SneakyThrows;
import org.apache.catalina.connector.ResponseFacade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class MetricsFilterTest {

    private static final String METHOD = "GET";
    private static final String PATH = "/actuator/prometheus";

    private final MockFilterChain chain = new MockFilterChain();
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final MetricsFilter metricsFilter = new MetricsFilter(meterRegistry);
    private final MockHttpServletRequest request = new MockHttpServletRequest(METHOD, PATH);

    @Mock
    private ResponseFacade response;

    @Test
    @SneakyThrows
    void filterOnSuccess() {
        long contentLength = 100L;
        long responseSize = 200L;
        request.setAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE, PATH);
        request.addHeader(CONTENT_LENGTH, contentLength);
        doReturn(responseSize).when(response).getContentWritten();

        metricsFilter.doFilter(request, response, chain);

        assertThat(meterRegistry.find(REQUEST_BYTES).tags("method", METHOD, "uri", PATH))
                .isNotNull()
                .extracting(Search::summary)
                .isNotNull()
                .returns(1L, DistributionSummary::count)
                .returns((double) contentLength, DistributionSummary::totalAmount);
        assertThat(meterRegistry.find(RESPONSE_BYTES).tags("method", METHOD, "uri", PATH))
                .isNotNull()
                .extracting(Search::summary)
                .isNotNull()
                .returns(1L, DistributionSummary::count)
                .returns((double) responseSize, DistributionSummary::totalAmount);
    }
}
