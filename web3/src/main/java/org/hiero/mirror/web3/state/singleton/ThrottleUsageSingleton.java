// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import static com.hedera.node.app.throttle.schemas.V0490CongestionThrottleSchema.THROTTLE_USAGE_SNAPSHOTS_KEY;

import com.hedera.hapi.node.state.throttles.ThrottleUsageSnapshots;
import jakarta.inject.Named;

@Named
public class ThrottleUsageSingleton implements SingletonState<ThrottleUsageSnapshots> {

    @Override
    public String getKey() {
        return THROTTLE_USAGE_SNAPSHOTS_KEY;
    }

    @Override
    public ThrottleUsageSnapshots get() {
        return ThrottleUsageSnapshots.DEFAULT;
    }
}
