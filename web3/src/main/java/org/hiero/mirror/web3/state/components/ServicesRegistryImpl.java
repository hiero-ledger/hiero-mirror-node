// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.components;

import com.hedera.node.app.services.ServicesRegistry;
import com.swirlds.state.lifecycle.Service;
import jakarta.inject.Named;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.state.keyvalue.StateRegistry;
import org.jspecify.annotations.NullMarked;

@CustomLog
@Named
@NullMarked
@RequiredArgsConstructor
public final class ServicesRegistryImpl implements ServicesRegistry {

    private final Set<Registration> entries = new TreeSet<>();
    private final StateRegistry stateRegistry;

    @Override
    public Set<Registration> registrations() {
        return Collections.unmodifiableSet(entries);
    }

    @Override
    public void register(Service service) {
        final var serviceName = service.getServiceName();
        final var registry = new SchemaRegistryImpl(serviceName, stateRegistry);
        service.registerSchemas(registry);
        entries.add(new ServicesRegistryImpl.Registration(service, registry));
        log.debug("Registered service {} with implementation {}", serviceName, service.getClass());
    }

    @Override
    public ServicesRegistry subRegistryFor(String... serviceNames) {
        return this;
    }
}
