// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import com.swirlds.state.spi.ReadableKVStateBase;
import jakarta.annotation.Nonnull;
import java.util.Collections;
import java.util.Iterator;

public abstract class AbstractReadableKVState<K, V> extends ReadableKVStateBase<K, V> {

    protected AbstractReadableKVState(@Nonnull String serviceName, @Nonnull String stateKey) {
        super(serviceName, stateKey);
    }

    @Nonnull
    @Override
    protected Iterator<K> iterateFromDataSource() {
        return Collections.emptyIterator();
    }

    @Override
    @SuppressWarnings("deprecation")
    public long size() {
        return 0;
    }
}
