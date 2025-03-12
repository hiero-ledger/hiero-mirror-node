// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state.singleton;

import static com.hedera.node.app.throttle.schemas.V0490CongestionThrottleSchema.CONGESTION_LEVEL_STARTS_STATE_KEY;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.state.congestion.CongestionLevelStarts;
import org.junit.jupiter.api.Test;

class CongestionLevelStartsSingletonTest {

    private final CongestionLevelStartsSingleton congestionLevelStartsSingleton = new CongestionLevelStartsSingleton();

    @Test
    void get() {
        assertThat(congestionLevelStartsSingleton.get()).isEqualTo(CongestionLevelStarts.DEFAULT);
    }

    @Test
    void key() {
        assertThat(congestionLevelStartsSingleton.getKey()).isEqualTo(CONGESTION_LEVEL_STARTS_STATE_KEY);
    }
}
