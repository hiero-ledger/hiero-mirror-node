// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import jakarta.inject.Named;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.CommonProperties;
import org.springframework.web.filter.OncePerRequestFilter;

@Named
@RequiredArgsConstructor
class ClusterIdFilter extends OncePerRequestFilter {

    private final CommonProperties properties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        response.setHeader(CommonProperties.CLUSTER_ID_HEADER, properties.getClusterId());
        filterChain.doFilter(request, response);
    }
}
