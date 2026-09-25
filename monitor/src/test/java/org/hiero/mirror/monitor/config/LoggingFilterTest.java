// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(OutputCaptureExtension.class)
class LoggingFilterTest {

    private static final Duration WAIT = Duration.ofSeconds(10L);

    private final LoggingFilter loggingFilter = new LoggingFilter();

    @Test
    void filterOnSuccess(CapturedOutput output) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build());
        exchange.getResponse().setRawStatusCode(200);

        StepVerifier.withVirtualTime(() -> loggingFilter.filter(
                        exchange,
                        serverWebExchange ->
                                Mono.defer(() -> exchange.getResponse().setComplete())))
                .thenAwait(WAIT)
                .expectComplete()
                .verify(WAIT);

        assertLog(output, "INFO", "\\w+ GET / in \\d+ ms: 200");
    }

    @Test
    void filterPath(CapturedOutput output) {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/").build());
        exchange.getResponse().setRawStatusCode(200);

        StepVerifier.withVirtualTime(() -> loggingFilter.filter(
                        exchange,
                        serverWebExchange ->
                                Mono.defer(() -> exchange.getResponse().setComplete())))
                .thenAwait(WAIT)
                .expectComplete()
                .verify(WAIT);

        assertThat(output).asString().isEmpty();
    }

    @Test
    void filterLogsUri(CapturedOutput output) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("https://evil.example:8443/health?foo=bar")
                .build());
        exchange.getResponse().setRawStatusCode(200);

        StepVerifier.withVirtualTime(() -> loggingFilter.filter(
                        exchange,
                        serverWebExchange ->
                                Mono.defer(() -> exchange.getResponse().setComplete())))
                .thenAwait(WAIT)
                .expectComplete()
                .verify(WAIT);

        assertLog(output, "INFO", "\\w+ GET https://evil\\.example:8443/health\\?foo=bar in \\d+ ms: 200");
    }

    @Test
    void filterXForwardedFor(CapturedOutput output) {
        String clientIp = "10.0.0.100";
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/")
                .header(LoggingFilter.X_FORWARDED_FOR, clientIp)
                .build());
        exchange.getResponse().setRawStatusCode(200);

        StepVerifier.withVirtualTime(() -> loggingFilter.filter(
                        exchange,
                        serverWebExchange ->
                                Mono.defer(() -> exchange.getResponse().setComplete())))
                .thenAwait(WAIT)
                .expectComplete()
                .verify(WAIT);

        assertLog(output, "INFO", clientIp + " GET / in \\d+ ms: 200");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "10.0.0.100\r\nWARN injected",
                "10.0.0.100\nINFO fake",
                "10.0.0.100\tinjected",
                "10.0.0.100\u0000injected"
            })
    void filterSanitizesXForwardedFor(String clientIp, CapturedOutput output) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/")
                .header(LoggingFilter.X_FORWARDED_FOR, clientIp)
                .build());
        exchange.getResponse().setRawStatusCode(200);

        StepVerifier.withVirtualTime(() -> loggingFilter.filter(
                        exchange,
                        serverWebExchange ->
                                Mono.defer(() -> exchange.getResponse().setComplete())))
                .thenAwait(WAIT)
                .expectComplete()
                .verify(WAIT);

        var sanitizedClient = LoggingFilter.sanitize(clientIp);
        assertLog(output, "INFO", sanitizedClient + " GET / in \\d+ ms: 200");
        assertNoControlCharacters(output);
    }

    @Test
    @SneakyThrows
    void filterSanitizesUriControlCharacters(CapturedOutput output) {
        var uri = new URI("http", "localhost", "/health\ninjected\rpath\tquery", "foo=bar\nbaz", null);
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.GET, uri).build());
        exchange.getResponse().setRawStatusCode(200);

        StepVerifier.withVirtualTime(() -> loggingFilter.filter(
                        exchange,
                        serverWebExchange ->
                                Mono.defer(() -> exchange.getResponse().setComplete())))
                .thenAwait(WAIT)
                .expectComplete()
                .verify(WAIT);

        assertLog(
                output,
                "INFO",
                "\\w+ GET http://localhost/health%0Ainjected%0Dpath%09query\\?foo=bar%0Abaz in \\d+ ms: 200");
        assertNoControlCharacters(output);
    }

    @Test
    void filterOnCancel(CapturedOutput output) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build());

        StepVerifier.withVirtualTime(() -> loggingFilter.filter(
                        exchange, serverWebExchange -> exchange.getResponse().setComplete()))
                .thenCancel()
                .verify(WAIT);

        assertLog(output, "WARN", "\\w+ GET / in \\d+ ms: cancelled");
    }

    @Test
    void filterOnCancelActuator(CapturedOutput output) {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/prometheus").build());

        StepVerifier.withVirtualTime(() -> loggingFilter.filter(
                        exchange, serverWebExchange -> exchange.getResponse().setComplete()))
                .thenCancel()
                .verify(WAIT);

        assertThat(output).asString().isEmpty();
    }

    @Test
    void filterOnError(CapturedOutput output) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build());
        exchange.getResponse().setRawStatusCode(500);

        var exception = new IllegalArgumentException("error");
        StepVerifier.withVirtualTime(() -> loggingFilter
                        .filter(exchange, serverWebExchange -> Mono.error(exception))
                        .onErrorResume(t -> exchange.getResponse().setComplete()))
                .thenAwait(WAIT)
                .expectComplete()
                .verify(WAIT);

        assertLog(output, "WARN", "\\w+ GET / in \\d+ ms: " + exception.getMessage());
    }

    @Test
    void sanitize() {
        assertThat(LoggingFilter.sanitize("abc")).isEqualTo("abc");
        assertThat(LoggingFilter.sanitize("10.0.0.1")).isEqualTo("10.0.0.1");
        assertThat(LoggingFilter.sanitize("/health?foo=bar")).isEqualTo("/health?foo=bar");
        assertThat(LoggingFilter.sanitize("a\rb\nc\td")).isEqualTo("a_b_c_d");
        assertThat(LoggingFilter.sanitize("error\r\nWARN injected")).isEqualTo("error__WARN injected");
        assertThat(LoggingFilter.sanitize("\u0000injected")).isEqualTo("_injected");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void sanitizeEmpty(String value) {
        assertThat(LoggingFilter.sanitize(value)).isEqualTo(value);
    }

    private void assertLog(CapturedOutput logOutput, String level, String pattern) {
        assertThat(logOutput).asString().hasLineCount(1).contains(level).containsPattern(pattern);
    }

    private void assertNoControlCharacters(CapturedOutput logOutput) {
        assertThat(logOutput.toString().stripTrailing())
                .doesNotContain("\r")
                .doesNotContain("\n")
                .doesNotContain("\t")
                .doesNotContain("\0");
    }
}
