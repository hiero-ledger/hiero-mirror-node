// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.graphql.config;

import static com.google.common.net.HttpHeaders.X_FORWARDED_FOR;
import static org.assertj.core.api.Assertions.assertThat;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.ForwardedHeaderFilter;
import org.springframework.web.util.WebUtils;

@ExtendWith(OutputCaptureExtension.class)
class LoggingFilterTest {
    private final LoggingFilter loggingFilter = new LoggingFilter();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final MockFilterChain chain = new MockFilterChain();

    @Test
    @SneakyThrows
    void filterOnSuccess(CapturedOutput output) {
        var request = new MockHttpServletRequest("GET", "/");

        response.setStatus(HttpStatus.OK.value());

        loggingFilter.doFilter(request, response, chain);

        assertLog(output, "INFO", "\\w+ GET / in \\d+ ms: 200 Success");
    }

    @Test
    @SneakyThrows
    void filterPath(CapturedOutput output) {
        var request = new MockHttpServletRequest("GET", "/actuator/");

        response.setStatus(HttpStatus.OK.value());

        loggingFilter.doFilter(request, response, chain);

        assertThat(output).asString().isEmpty();
    }

    @Test
    @SneakyThrows
    void filterXForwardedFor(CapturedOutput output) {
        String clientIp = "10.0.0.100";
        var request = new MockHttpServletRequest("GET", "/");
        request.addHeader(X_FORWARDED_FOR, clientIp);
        response.setStatus(HttpStatus.OK.value());

        new ForwardedHeaderFilter()
                .doFilter(
                        request, response, (request1, response1) -> loggingFilter.doFilter(request1, response1, chain));

        assertLog(output, "INFO", clientIp + " GET / in \\d+ ms: 200");
    }

    @Test
    @SneakyThrows
    void filterOnError(CapturedOutput output) {
        var request = new MockHttpServletRequest("GET", "/");
        var exception = new IllegalArgumentException("error");

        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());

        loggingFilter.doFilter(request, response, (request1, response1) -> {
            throw exception;
        });

        assertLog(output, "WARN", "\\w+ GET / in \\d+ ms: 500 " + exception.getMessage());
    }

    @Test
    @SneakyThrows
    void filterOnErrorAttribute(CapturedOutput output) {
        var request = new MockHttpServletRequest("GET", "/");
        var exception = new IllegalArgumentException("error");
        request.setAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE, exception);

        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());

        loggingFilter.doFilter(request, response, (req, res) -> {});

        assertLog(output, "WARN", "\\w+ GET / in \\d+ ms: 500 " + exception.getMessage());
    }

    private void assertLog(CapturedOutput logOutput, String level, String pattern) {
        assertThat(logOutput).asString().hasLineCount(1).contains(level).containsPattern(pattern);
    }
}
