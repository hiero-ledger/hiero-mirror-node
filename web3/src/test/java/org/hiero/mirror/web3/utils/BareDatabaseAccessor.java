// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.utils;

import java.util.Optional;
import org.hiero.mirror.web3.evm.store.accessor.DatabaseAccessor;
import org.jspecify.annotations.NonNull;

public class BareDatabaseAccessor<K, V> extends DatabaseAccessor<K, V> {
    @NonNull
    @Override
    public Optional<V> get(@NonNull final K key, Optional<Long> timestamp) {
        throw new UnsupportedOperationException("BareGroundTruthAccessor.get");
    }
}
