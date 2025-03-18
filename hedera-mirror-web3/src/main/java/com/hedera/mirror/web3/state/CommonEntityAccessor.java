// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state;

import static com.hedera.services.utils.EntityIdUtils.toEntityId;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.repository.EntityRepository;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
public class CommonEntityAccessor {
    private final EntityRepository entityRepository;
    private static final CommonProperties COMMON_PROPERTIES = CommonProperties.getInstance();
    private static final long SHARD = COMMON_PROPERTIES.getShard();
    private static final long REALM = COMMON_PROPERTIES.getRealm();

    public @Nonnull Optional<Entity> get(@Nonnull final AccountID accountID, final Optional<Long> timestamp) {
        if (accountID.hasAccountNum()) {
            return get(toEntityId(accountID), timestamp);
        } else {
            return get(accountID.alias(), timestamp);
        }
    }

    public @Nonnull Optional<Entity> get(@Nonnull final Bytes alias, final Optional<Long> timestamp) {
        return timestamp
                .map(t -> entityRepository.findActiveByEvmAddressOrAliasAndTimestampAndShardAndRealm(
                        alias.toByteArray(), t, SHARD, REALM))
                .orElseGet(() ->
                        entityRepository.findByEvmAddressOrAliasAndShardAndRealm(alias.toByteArray(), SHARD, REALM));
    }

    public @Nonnull Optional<Entity> get(@Nonnull final TokenID tokenID, final Optional<Long> timestamp) {
        return get(toEntityId(tokenID), timestamp);
    }

    public @Nonnull Optional<Entity> get(@Nonnull final EntityId entityId, final Optional<Long> timestamp) {
        return timestamp
                .map(t -> entityRepository.findActiveByIdAndTimestamp(entityId.getId(), t))
                .orElseGet(() -> entityRepository.findByIdAndDeletedIsFalse(entityId.getId()));
    }

    public Optional<Entity> getEntityByEvmAddressTimestampShardAndRealm(
            final byte[] addressBytes, final Optional<Long> timestamp) {
        return timestamp
                .map(t -> entityRepository.findActiveByEvmAddressAndTimestampAndShardAndRealm(
                        addressBytes, t, SHARD, REALM))
                .orElseGet(() ->
                        entityRepository.findByEvmAddressAndDeletedIsFalseAndShardAndRealm(addressBytes, SHARD, REALM));
    }
}
