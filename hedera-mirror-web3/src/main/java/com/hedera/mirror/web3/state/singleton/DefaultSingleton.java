// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state.singleton;

import com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema;
import com.hedera.node.app.service.token.impl.schemas.V0610TokenSchema;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DefaultSingleton extends AtomicReference<Object> implements SingletonState<Object> {

    private static final Set<String> keys = Stream.of(
                    // Not implemented but not needed
                    V0490TokenSchema.STAKING_NETWORK_REWARDS_KEY, V0610TokenSchema.NODE_REWARDS_KEY)
            .collect(Collectors.toSet());
    private final String key;

    public String getKey() {
        if (keys.contains(key)) {
            return key;
        }
        throw new UnsupportedOperationException("Unsupported singleton key: " + key);
    }
}
