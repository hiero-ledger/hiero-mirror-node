// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state.components;

import com.hedera.mirror.web3.state.keyvalue.StateKeyRegistry;
import com.hedera.mirror.web3.state.singleton.SingletonState;
import com.hedera.node.app.services.ServicesRegistry;
import com.hedera.node.app.state.merkle.SchemaApplications;
import com.swirlds.state.lifecycle.Service;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
public class ServicesRegistryImpl implements ServicesRegistry {

    private final SortedSet<Registration> entries = new TreeSet<>();
    private final Collection<SingletonState<?>> singletons;
    private final StateKeyRegistry stateKeyRegistry;

    @Nonnull
    @Override
    public Set<Registration> registrations() {
        return Collections.unmodifiableSortedSet(entries);
    }

    @Override
    public void register(@Nonnull Service service) {
        final var registry = new SchemaRegistryImpl(singletons, new SchemaApplications(), stateKeyRegistry);
        service.registerSchemas(registry);
        entries.add(new ServicesRegistryImpl.Registration(service, registry));
    }

    @Nonnull
    @Override
    public ServicesRegistry subRegistryFor(@Nonnull String... serviceNames) {
        final var selections = Set.of(serviceNames);
        final var subRegistry = new ServicesRegistryImpl(singletons, stateKeyRegistry);
        subRegistry.entries.addAll(entries.stream()
                .filter(registration -> selections.contains(registration.serviceName()))
                .toList());
        return subRegistry;
    }
}
