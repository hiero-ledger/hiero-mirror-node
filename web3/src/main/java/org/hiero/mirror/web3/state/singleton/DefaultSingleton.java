// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.atomic.AtomicReference;

public class DefaultSingleton extends AtomicReference<Object> implements SingletonState<Object> {

    private final String serviceName;
    private final int id;

    public DefaultSingleton(final String serviceName, final int id, final Object defaultValue) {
        super(requireNonNull(defaultValue));
        this.serviceName = serviceName;
        this.id = id;
    }

    @Override
    public int getStateId() {
        return id;
    }

    @Override
    public String getServiceName() {
        return serviceName;
    }
}
