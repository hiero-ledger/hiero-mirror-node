// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.config;

import jakarta.inject.Named;
import java.io.Serial;
import java.net.InetSocketAddress;
import java.net.URI;
import lombok.CustomLog;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.CollectionUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@CustomLog
@Named
class LoggingFilter implements WebFilter {

    static final String X_FORWARDED_FOR = "X-Forwarded-For";

    @SuppressWarnings("java:S1075")
    private static final String ACTUATOR_PATH = "/actuator/";

    private static final String LOCALHOST = "127.0.0.1";
    private static final String LOG_FORMAT = "{} {} {} in {} ms: {}";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long start = System.currentTimeMillis();
        return chain.filter(exchange)
                .transformDeferred(call -> call.doOnEach(signal -> doFilter(exchange, signal.getThrowable(), start))
                        .doOnCancel(() -> doFilter(exchange, new CancelledException(), start)));
    }

    private void doFilter(ServerWebExchange exchange, Throwable cause, long start) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted() || cause instanceof CancelledException) {
            logRequest(exchange, start, cause);
        } else {
            response.beforeCommit(() -> {
                logRequest(exchange, start, cause);
                return Mono.empty();
            });
        }
    }

    private void logRequest(ServerWebExchange exchange, long startTime, Throwable cause) {
        long elapsed = System.currentTimeMillis() - startTime;
        ServerHttpRequest request = exchange.getRequest();
        URI uri = request.getURI();
        var message =
                cause != null ? cause.getMessage() : exchange.getResponse().getStatusCode();
        var params = new Object[] {getClient(request), request.getMethod(), uri, elapsed, message};

        if (Strings.CS.startsWith(uri.getPath(), ACTUATOR_PATH)) {
            log.debug(LOG_FORMAT, params);
        } else if (cause != null) {
            log.warn(LOG_FORMAT, params);
        } else {
            log.info(LOG_FORMAT, params);
        }
    }

    private String getClient(ServerHttpRequest request) {
        String xForwardedFor = CollectionUtils.firstElement(request.getHeaders().get(X_FORWARDED_FOR));

        if (StringUtils.isNotBlank(xForwardedFor)) {
            return xForwardedFor;
        }

        InetSocketAddress remoteAddress = request.getRemoteAddress();

        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().toString();
        }

        return LOCALHOST;
    }

    private static class CancelledException extends RuntimeException {
        private static final String MESSAGE = "cancelled";

        @Serial
        private static final long serialVersionUID = -1065743479862315529L;

        CancelledException() {
            super(MESSAGE);
        }
    }
}
