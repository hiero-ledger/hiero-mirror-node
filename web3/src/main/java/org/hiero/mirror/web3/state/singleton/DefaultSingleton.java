// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DefaultSingleton extends AtomicReference<Object> implements SingletonState<Object> {

    private final String serviceName;
    private final int id;

    @Override
    public int getStateId() {
        return id;
    }

    @Override
    public String getServiceName() {
        return serviceName;
    }
}
