// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.components;

import com.hedera.node.app.services.ServicesRegistry;
import com.swirlds.state.lifecycle.Service;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.util.Collections;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.state.keyvalue.StateRegistry;

@Named
@RequiredArgsConstructor
@CustomLog
public class ServicesRegistryImpl implements ServicesRegistry {

    private final SortedSet<Registration> entries = new TreeSet<>();
    private final StateRegistry stateRegistry;

    @Nonnull
    @Override
    public Set<Registration> registrations() {
        return Collections.unmodifiableSortedSet(entries);
    }

    @Override
    public void register(@Nonnull Service service) {
        final var serviceName = service.getServiceName();

        log.debug("Registering schemas for service {}", serviceName);
        final var registry = new SchemaRegistryImpl(serviceName, stateRegistry);
        service.registerSchemas(registry);
        entries.add(new ServicesRegistryImpl.Registration(service, registry));
        log.info("Registered service {} with implementation {}", service.getServiceName(), service.getClass());
    }

    @Nonnull
    @Override
    public ServicesRegistry subRegistryFor(@Nonnull String... serviceNames) {
        return this;
    }
}
