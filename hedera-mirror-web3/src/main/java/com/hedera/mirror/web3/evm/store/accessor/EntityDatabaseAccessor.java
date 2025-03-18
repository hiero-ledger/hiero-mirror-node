// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.evm.store.accessor;

import static com.hedera.mirror.common.util.DomainUtils.EVM_ADDRESS_LENGTH;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.entityIdNumFromEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases.isMirror;

import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.evm.store.DatabaseBackedStateFrame.DatabaseAccessIncorrectKeyTypeException;
import com.hedera.mirror.web3.repository.EntityRepository;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

@Named
@RequiredArgsConstructor
public class EntityDatabaseAccessor extends DatabaseAccessor<Object, Entity> {
    private final EntityRepository entityRepository;
    private final CommonProperties commonProperties;

    @Override
    public @Nonnull Optional<Entity> get(@Nonnull Object key, final Optional<Long> timestamp) {
        if (key instanceof Address address) {
            final var addressBytes = address.toArrayUnsafe();
            if (isMirror(addressBytes)) {
                return getEntityByMirrorAddressAndTimestamp(address, timestamp);
            } else {
                return getEntityByEvmAddressTimestampShardAndRealm(addressBytes, timestamp);
            }
        }
        throw new DatabaseAccessIncorrectKeyTypeException("Accessor for class %s failed to fetch by key of type %s"
                .formatted(Entity.class.getTypeName(), key.getClass().getTypeName()));
    }

    private Optional<Entity> getEntityByMirrorAddressAndTimestamp(Address address, final Optional<Long> timestamp) {
        final var entityId = entityIdNumFromEvmAddress(address);
        return timestamp
                .map(t -> entityRepository.findActiveByIdAndTimestamp(entityId, t))
                .orElseGet(() -> entityRepository.findByIdAndDeletedIsFalse(entityId));
    }

    private Optional<Entity> getEntityByEvmAddressTimestampShardAndRealm(byte[] addressBytes, final Optional<Long> timestamp) {
        return timestamp
                .map(t -> entityRepository.findActiveByEvmAddressAndTimestampAndShardAndRealm(addressBytes, t, commonProperties.getShard(), commonProperties.getRealm()))
                .orElseGet(() -> entityRepository.findByEvmAddressAndDeletedIsFalseAndShardAndRealm(addressBytes, commonProperties.getShard(), commonProperties.getRealm()));
    }

    public Address evmAddressFromId(EntityId entityId, final Optional<Long> timestamp) {
        Entity entity = timestamp
                .map(t -> entityRepository
                        .findActiveByIdAndTimestamp(entityId.getId(), t)
                        .orElse(null))
                .orElseGet(() -> entityRepository
                        .findByIdAndDeletedIsFalse(entityId.getId())
                        .orElse(null));
        if (entity == null) {
            return Address.ZERO;
        }

        if (entity.getEvmAddress() != null) {
            return Address.wrap(Bytes.wrap(entity.getEvmAddress()));
        }

        if (entity.getAlias() != null && entity.getAlias().length == EVM_ADDRESS_LENGTH) {
            return Address.wrap(Bytes.wrap(entity.getAlias()));
        }

        return toAddress(entityId);
    }
}
