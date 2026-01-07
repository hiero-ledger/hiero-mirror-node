// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import static com.hedera.node.app.throttle.schemas.V0490CongestionThrottleSchema.THROTTLE_USAGE_SNAPSHOTS_STATE_ID;

import com.hedera.hapi.node.state.throttles.ThrottleUsageSnapshots;
import com.hedera.node.app.throttle.CongestionThrottleService;
import jakarta.inject.Named;
import org.hiero.mirror.web3.state.RegisterableState;

@Named
final class ThrottleUsageSingleton implements SingletonState<ThrottleUsageSnapshots>, RegisterableState {

    @Override
    public Integer getId() {
        return THROTTLE_USAGE_SNAPSHOTS_STATE_ID;
    }

    @Override
    public String getServiceName() {
        return CongestionThrottleService.NAME;
    }

    @Override
    public ThrottleUsageSnapshots get() {
        return ThrottleUsageSnapshots.DEFAULT;
    }
}
