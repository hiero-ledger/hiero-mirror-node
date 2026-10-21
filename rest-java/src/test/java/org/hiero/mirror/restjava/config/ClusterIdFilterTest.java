// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.ServletException;
import lombok.SneakyThrows;
import org.hiero.mirror.common.CommonProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ClusterIdFilterTest {

    private final CommonProperties properties = new CommonProperties();
    private final ClusterIdFilter filter = new ClusterIdFilter(properties);
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    @SneakyThrows
    void setsConfiguredClusterId() {
        properties.setClusterId("abc1234");

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(CommonProperties.CLUSTER_ID_HEADER)).isEqualTo("abc1234");
    }

    @Test
    void setsClusterIdWhenChainFails() {
        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
                    throw new ServletException("boom");
                }))
                .isInstanceOf(ServletException.class);

        assertThat(response.getHeader(CommonProperties.CLUSTER_ID_HEADER)).isEqualTo(properties.getClusterId());
    }
}
