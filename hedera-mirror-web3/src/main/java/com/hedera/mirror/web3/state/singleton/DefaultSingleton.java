// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state.singleton;

import com.hedera.node.app.fees.schemas.V0490FeeSchema;
import com.hedera.node.app.ids.schemas.V0490EntityIdSchema;
import com.hedera.node.app.ids.schemas.V0590EntityIdSchema;
import com.hedera.node.app.records.schemas.V0490BlockRecordSchema;
import com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema;
import com.hedera.node.app.service.token.impl.schemas.V0610TokenSchema;
import com.hedera.node.app.throttle.schemas.V0490CongestionThrottleSchema;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DefaultSingleton extends AtomicReference<Object> implements SingletonState<Object> {

    private static final Set<String> keys = Stream.of(
                    // Implemented
                    V0490BlockRecordSchema.BLOCK_INFO_STATE_KEY,
                    V0490CongestionThrottleSchema.CONGESTION_LEVEL_STARTS_STATE_KEY,
                    V0490CongestionThrottleSchema.THROTTLE_USAGE_SNAPSHOTS_STATE_KEY,
                    V0490EntityIdSchema.ENTITY_ID_STATE_KEY,
                    V0590EntityIdSchema.ENTITY_COUNTS_KEY,
                    V0490FeeSchema.MIDNIGHT_RATES_STATE_KEY,
                    V0490BlockRecordSchema.RUNNING_HASHES_STATE_KEY,
                    // Not implemented but not needed
                    V0490TokenSchema.STAKING_NETWORK_REWARDS_KEY,
                    V0610TokenSchema.NODE_REWARDS_KEY)
            .collect(Collectors.toSet());
    private final String key;

    public String getKey() {
        if (keys.contains(key)) {
            return key;
        }
        throw new UnsupportedOperationException("Unsupported singleton key: " + key);
    }
}
