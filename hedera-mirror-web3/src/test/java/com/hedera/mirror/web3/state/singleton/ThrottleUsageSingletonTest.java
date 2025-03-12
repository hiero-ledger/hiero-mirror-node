// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state.singleton;

import static com.hedera.node.app.throttle.schemas.V0490CongestionThrottleSchema.THROTTLE_USAGE_SNAPSHOTS_STATE_KEY;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.state.throttles.ThrottleUsageSnapshots;
import org.junit.jupiter.api.Test;

class ThrottleUsageSingletonTest {

    private final ThrottleUsageSingleton throttleUsageSingleton = new ThrottleUsageSingleton();

    @Test
    void get() {
        assertThat(throttleUsageSingleton.get()).isEqualTo(ThrottleUsageSnapshots.DEFAULT);
    }

    @Test
    void key() {
        assertThat(throttleUsageSingleton.getKey()).isEqualTo(THROTTLE_USAGE_SNAPSHOTS_STATE_KEY);
    }
}
