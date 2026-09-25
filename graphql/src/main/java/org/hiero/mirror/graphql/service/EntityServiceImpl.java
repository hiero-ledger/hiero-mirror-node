// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.graphql.service;

import static org.hiero.mirror.graphql.util.GraphQlUtils.decodeBase32;
import static org.hiero.mirror.graphql.util.GraphQlUtils.decodeEvmAddress;

import jakarta.inject.Named;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.graphql.repository.EntityRepository;

@Named
@RequiredArgsConstructor
public class EntityServiceImpl implements EntityService {

    private final EntityRepository entityRepository;

    @Override
    public Optional<Entity> getByIdAndType(EntityId entityId, EntityType type) {
        return entityRepository.findById(entityId.getId()).filter(e -> e.getType() == type);
    }

    @Override
    public Optional<Entity> getByAliasAndType(String alias, EntityType type) {
        return entityRepository.findByAlias(decodeBase32(alias)).filter(e -> e.getType() == type);
    }

    @Override
    public Optional<Entity> getByEvmAddressAndType(String evmAddress, EntityType type) {
        final var evmAddressBytes = decodeEvmAddress(evmAddress);
        final var entityId = DomainUtils.fromEvmAddress(evmAddressBytes);
        if (entityId != null) {
            return entityRepository.findById(entityId.getId()).filter(e -> e.getType() == type);
        }
        return entityRepository.findByEvmAddress(evmAddressBytes).filter(e -> e.getType() == type);
    }
}
