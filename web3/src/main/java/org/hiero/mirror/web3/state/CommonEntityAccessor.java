// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state;

import static com.hedera.services.utils.EntityIdUtils.toEntityId;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.repository.EntityRepository;

@Named
@RequiredArgsConstructor
public class CommonEntityAccessor {
    private final EntityRepository entityRepository;
    private final CommonProperties commonProperties;

    public @Nonnull Optional<Entity> get(@Nonnull final AccountID accountID, final Optional<Long> timestamp) {
        if (accountID.hasAccountNum()) {
            return get(toEntityId(accountID), timestamp);
        } else {
            return get(accountID.alias(), timestamp);
        }
    }

    public @Nonnull Optional<Entity> get(@Nonnull final Bytes alias, final Optional<Long> timestamp) {
        return timestamp
                .map(t -> entityRepository.findActiveByShardAndRealmAndEvmAddressOrAliasAndTimestamp(
                        commonProperties.getShard(), commonProperties.getRealm(), alias.toByteArray(), t))
                .orElseGet(() -> entityRepository.findByShardAndRealmAndEvmAddressOrAliasAndDeletedIsFalse(
                        commonProperties.getShard(), commonProperties.getRealm(), alias.toByteArray()));
    }

    public @Nonnull Optional<Entity> get(@Nonnull final TokenID tokenID, final Optional<Long> timestamp) {
        return get(toEntityId(tokenID), timestamp);
    }

    public @Nonnull Optional<Entity> get(@Nonnull final EntityId entityId, final Optional<Long> timestamp) {
        return timestamp
                .map(t -> entityRepository.findActiveByIdAndTimestamp(entityId.getId(), t))
                .orElseGet(() -> entityRepository.findByIdAndDeletedIsFalse(entityId.getId()));
    }

    public Optional<Entity> getEntityByEvmAddressAndTimestamp(
            final byte[] addressBytes, final Optional<Long> timestamp) {
        return timestamp
                .map(t -> entityRepository.findActiveByShardAndRealmAndEvmAddressAndTimestamp(
                        commonProperties.getShard(), commonProperties.getRealm(), addressBytes, t))
                .orElseGet(() -> entityRepository.findByShardAndRealmAndEvmAddressAndDeletedIsFalse(
                        commonProperties.getShard(), commonProperties.getRealm(), addressBytes));
    }
}
