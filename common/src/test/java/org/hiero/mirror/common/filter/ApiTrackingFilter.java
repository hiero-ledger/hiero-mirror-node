// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

public class ApiTrackingFilter implements Filter {

    private static final ThreadLocal<String> currentEndpoint = new ThreadLocal<>();

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException {
        final var httpRequest = (HttpServletRequest) request;
        currentEndpoint.set(httpRequest.getRequestURI());
        chain.doFilter(request, response);
        currentEndpoint.remove();
    }

    public static String getCurrentEndpoint() {
        return currentEndpoint.get();
    }
}
