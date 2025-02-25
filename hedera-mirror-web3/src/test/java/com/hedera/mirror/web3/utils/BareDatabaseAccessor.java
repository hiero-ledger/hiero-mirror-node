// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.utils;

import com.hedera.mirror.web3.evm.store.accessor.DatabaseAccessor;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;

public class BareDatabaseAccessor<K, V> extends DatabaseAccessor<K, V> {
    @NonNull
    @Override
    public Optional<V> get(@NonNull final K key, Optional<Long> timestamp) {
        throw new UnsupportedOperationException("BareGroundTruthAccessor.get");
    }
}
