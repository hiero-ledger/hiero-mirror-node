// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hiero.mirror.web3.exception.InvalidRequestHeaderException;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@NullMarked
@ConditionalOnProperty(prefix = "hiero.mirror.web3.opcode.tracer", name = "enabled", havingValue = "true")
public class AcceptEncodingInterceptor implements HandlerInterceptor {

    public static String MISSING_GZIP_HEADER_MESSAGE = "Accept-Encoding: gzip header is required";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        final var acceptEncodingHeader = request.getHeader(HttpHeaders.ACCEPT_ENCODING);

        if (acceptEncodingHeader == null || !acceptEncodingHeader.toLowerCase().contains("gzip")) {
            throw new InvalidRequestHeaderException(MISSING_GZIP_HEADER_MESSAGE);
        }
        return true;
    }
}
