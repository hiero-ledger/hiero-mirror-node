// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.throttle;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.web3.ApiEndpointName.ACTIONS;
import static org.hiero.mirror.web3.ApiEndpointName.OPCODES;
import static org.hiero.mirror.web3.ApiEndpointName.PRESTATE;

import org.hiero.mirror.web3.ApiEndpointName;
import org.hiero.mirror.web3.ApiProperties;
import org.hiero.mirror.web3.Web3Properties;
import org.hiero.mirror.web3.exception.ThrottleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class EndpointThrottleTest {

    private EndpointThrottle endpointThrottle;
    private Web3Properties web3Properties;

    @BeforeEach
    void setUp() {
        web3Properties = new Web3Properties();
        endpointThrottle = new EndpointThrottle(web3Properties);
    }

    @Test
    void allowsRequestsWhenEndpointThrottleIsDisabled() {
        setEndpointThrottle(OPCODES, 0);

        assertThatNoException().isThrownBy(() -> endpointThrottle.throttle(OPCODES, 1L));
        assertThatNoException().isThrownBy(() -> endpointThrottle.throttle(OPCODES, 1L));
    }

    @Test
    void consumesMultipleTokensAtomically() {
        setEndpointThrottle(ACTIONS, 5);

        endpointThrottle.throttle(ACTIONS, 3L);
        assertThatThrownBy(() -> endpointThrottle.throttle(ACTIONS, 3L))
                .isInstanceOf(ThrottleException.class)
                .hasMessageContaining("Requests per second rate limit exceeded");
        assertThatNoException().isThrownBy(() -> endpointThrottle.throttle(ACTIONS, 2L));
        assertThatThrownBy(() -> endpointThrottle.throttle(ACTIONS, 1L))
                .isInstanceOf(ThrottleException.class)
                .hasMessageContaining("Requests per second rate limit exceeded");
    }

    @Test
    void oversizedRequestDoesNotConsumeTokens() {
        setEndpointThrottle(ACTIONS, 2);

        assertThatThrownBy(() -> endpointThrottle.throttle(ACTIONS, 3L))
                .isInstanceOf(ThrottleException.class)
                .hasMessageContaining("Requests per second rate limit exceeded");
        assertThatNoException().isThrownBy(() -> endpointThrottle.throttle(ACTIONS, 2L));
        assertThatThrownBy(() -> endpointThrottle.throttle(ACTIONS, 1L))
                .isInstanceOf(ThrottleException.class)
                .hasMessageContaining("Requests per second rate limit exceeded");
    }

    @Test
    void usesSeparateBucketForEachEndpoint() {
        setEndpointThrottle(OPCODES, 1);
        setEndpointThrottle(PRESTATE, 1);

        endpointThrottle.throttle(OPCODES, 1L);
        assertThatThrownBy(() -> endpointThrottle.throttle(OPCODES, 1L)).isInstanceOf(ThrottleException.class);

        endpointThrottle.throttle(PRESTATE, 1L);
        assertThatThrownBy(() -> endpointThrottle.throttle(PRESTATE, 1L)).isInstanceOf(ThrottleException.class);
    }

    private void setEndpointThrottle(final ApiEndpointName endpoint, final long rateLimit) {
        final var apiProperties = new ApiProperties();
        apiProperties.getRequest().setThrottle(rateLimit);
        web3Properties.getApi().put(endpoint, apiProperties);
    }
}
