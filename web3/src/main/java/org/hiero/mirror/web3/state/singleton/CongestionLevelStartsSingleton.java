// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import static com.hedera.node.app.throttle.schemas.V0490CongestionThrottleSchema.CONGESTION_LEVEL_STARTS_KEY;

import com.hedera.hapi.node.state.congestion.CongestionLevelStarts;
import jakarta.inject.Named;

@Named
public class CongestionLevelStartsSingleton implements SingletonState<CongestionLevelStarts> {

    @Override
    public String getKey() {
        return CONGESTION_LEVEL_STARTS_KEY;
    }

    @Override
    public CongestionLevelStarts get() {
        return CongestionLevelStarts.DEFAULT;
    }
}
