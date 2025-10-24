// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import static com.hedera.node.app.throttle.schemas.V0490CongestionThrottleSchema.CONGESTION_LEVEL_STARTS_STATE_ID;

import com.hedera.hapi.node.state.congestion.CongestionLevelStarts;
import jakarta.inject.Named;

@Named
public class CongestionLevelStartsSingleton implements SingletonState<CongestionLevelStarts> {

    @Override
    public Integer getId() {
        return CONGESTION_LEVEL_STARTS_STATE_ID;
    }

    @Override
    public CongestionLevelStarts get() {
        return CongestionLevelStarts.DEFAULT;
    }
}
