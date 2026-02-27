// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import com.swirlds.state.spi.ReadableKVStateBase;
import java.util.Collections;
import java.util.Iterator;
import org.hiero.mirror.web3.state.RegisterableState;
import org.hiero.mirror.web3.state.core.ForwardingReadableKVStateBase;
import org.jspecify.annotations.NonNull;

public abstract class AbstractReadableKVState<K, V> extends ReadableKVStateBase<K, V> implements RegisterableState {

    protected AbstractReadableKVState(@NonNull String serviceName, int stateId) {
        super(stateId, serviceName, new ForwardingReadableKVStateBase<>(stateId));
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
