// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.graphql.service;

import static com.hedera.mirror.graphql.util.GraphQlUtils.decodeBase32;
import static com.hedera.mirror.graphql.util.GraphQlUtils.decodeEvmAddress;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.graphql.repository.EntityRepository;
import jakarta.inject.Named;
import java.nio.ByteBuffer;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

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
        byte[] evmAddressBytes = decodeEvmAddress(evmAddress);
        var buffer = ByteBuffer.wrap(evmAddressBytes);
        if (buffer.getInt() == 0 && buffer.getLong() == 0) {
            return entityRepository.findById(buffer.getLong()).filter(e -> e.getType() == type);
        }
        return entityRepository.findByEvmAddress(evmAddressBytes).filter(e -> e.getType() == type);
    }
}
