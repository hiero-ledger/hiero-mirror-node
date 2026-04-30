// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.hiero.mirror.common.tableusage.EndpointContext;
import org.hiero.mirror.common.tableusage.EndpointNormalizer;

public final class ApiTrackingFilter implements Filter {

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException {
        final var httpRequest = (HttpServletRequest) request;
        final var normalizedUri = EndpointNormalizer.normalize(httpRequest.getRequestURI());
        EndpointContext.setCurrentEndpoint(normalizedUri);
        chain.doFilter(request, response);
        EndpointContext.clearCurrentEndpoint();
    }
}
