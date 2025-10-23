// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import com.swirlds.state.spi.ReadableKVStateBase;
import java.util.Collections;
import java.util.Iterator;
import org.jspecify.annotations.NonNull;

public abstract class AbstractReadableKVState<K, V> extends ReadableKVStateBase<K, V> {

    protected AbstractReadableKVState(@NonNull String serviceName, @NonNull String stateKey) {
        super(serviceName, stateKey);
    }

    @NonNull
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
