// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.hiero.mirror.common.util.EndpointNormalizer;

public final class ApiTrackingFilter implements Filter {

    private static final ThreadLocal<String> CURRENT_ENDPOINT = new ThreadLocal<>();

    public static String getCurrentEndpoint() {
        return CURRENT_ENDPOINT.get();
    }

    public static void setCurrentEndpoint(String endpoint) {
        CURRENT_ENDPOINT.set(endpoint);
    }

    public static void clearCurrentEndpoint() {
        CURRENT_ENDPOINT.remove();
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException {
        final var httpRequest = (HttpServletRequest) request;
        final var normalizedUri = EndpointNormalizer.normalize(httpRequest.getRequestURI());
        CURRENT_ENDPOINT.set(normalizedUri);
        chain.doFilter(request, response);
        CURRENT_ENDPOINT.remove();
    }
}
