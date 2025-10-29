// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.restjava.common.EntityIdAliasParameter;
import org.hiero.mirror.restjava.common.EntityIdEvmAddressParameter;
import org.hiero.mirror.restjava.common.EntityIdNumParameter;
import org.hiero.mirror.restjava.common.EntityIdParameter;
import org.hiero.mirror.restjava.repository.EntityRepository;
import org.jspecify.annotations.NonNull;
import jakarta.inject.Named;
import jakarta.persistence.EntityNotFoundException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
class EntityServiceImpl implements EntityService {

    private final EntityRepository entityRepository;

    @Override
    public Entity findById(@NonNull EntityId id) {
        return entityRepository.findById(id.getId())
                .orElseThrow(() -> new EntityNotFoundException("Entity not found: " + id));
    }

    @Override
    public EntityId lookup(@NonNull EntityIdParameter accountId) {
        var id = switch (accountId) {
            case EntityIdNumParameter p -> Optional.of(p.id());
            case EntityIdAliasParameter p -> entityRepository.findByAlias(p.alias()).map(EntityId::of);
            case EntityIdEvmAddressParameter p -> entityRepository.findByEvmAddress(p.evmAddress()).map(EntityId::of);
        };

        return id.orElseThrow(() -> new EntityNotFoundException("No account found for the given ID"));
    }
}
