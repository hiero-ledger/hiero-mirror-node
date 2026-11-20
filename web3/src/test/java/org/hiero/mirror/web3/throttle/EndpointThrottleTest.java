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

        assertThatNoException().isThrownBy(() -> endpointThrottle.throttle(OPCODES));
        assertThatNoException().isThrownBy(() -> endpointThrottle.throttle(OPCODES));
    }

    @Test
    void rejectsRequestsAboveTheRateLimit() {
        setEndpointThrottle(ACTIONS, 2);

        assertThatNoException().isThrownBy(() -> endpointThrottle.throttle(ACTIONS));
        assertThatNoException().isThrownBy(() -> endpointThrottle.throttle(ACTIONS));
        assertThatThrownBy(() -> endpointThrottle.throttle(ACTIONS))
                .isInstanceOf(ThrottleException.class)
                .hasMessageContaining("Requests per second rate limit exceeded");
    }

    @Test
    void usesSeparateBucketForEachEndpoint() {
        setEndpointThrottle(OPCODES, 1);
        setEndpointThrottle(PRESTATE, 1);

        endpointThrottle.throttle(OPCODES);
        assertThatThrownBy(() -> endpointThrottle.throttle(OPCODES)).isInstanceOf(ThrottleException.class);

        endpointThrottle.throttle(PRESTATE);
        assertThatThrownBy(() -> endpointThrottle.throttle(PRESTATE)).isInstanceOf(ThrottleException.class);
    }

    private void setEndpointThrottle(final ApiEndpointName endpoint, final long rateLimit) {
        final var apiProperties = new ApiProperties();
        apiProperties.getRequest().setThrottle(rateLimit);
        web3Properties.getApi().put(endpoint, apiProperties);
    }
}
