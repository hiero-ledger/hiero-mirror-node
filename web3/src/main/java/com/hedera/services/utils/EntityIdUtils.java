// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.utils;

import static org.hiero.mirror.common.util.DomainUtils.fromEvmAddress;
import static org.hiero.mirror.common.util.DomainUtils.toEvmAddress;

import com.hedera.hapi.node.base.AccountID.AccountOneOfType;
import com.hedera.pbj.runtime.OneOf;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hyperledger.besu.datatypes.Address;

public final class EntityIdUtils {

    private EntityIdUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static AccountID accountIdFromEvmAddress(final Address address) {
        return accountIdFromEvmAddress(address.toArrayUnsafe());
    }

    public static AccountID accountIdFromEvmAddress(final byte[] bytes) {
        var entityId = fromEvmAddress(bytes);

        return entityId == null ? AccountID.getDefaultInstance() : entityId.toAccountID();
    }

    public static ContractID contractIdFromEvmAddress(final byte[] bytes) {
        var entityId = fromEvmAddress(bytes);

        return entityId == null ? ContractID.getDefaultInstance() : entityId.toContractID();
    }

    public static ContractID contractIdFromEvmAddress(final Address address) {
        return contractIdFromEvmAddress(address.toArrayUnsafe());
    }

    public static Address asTypedEvmAddress(final ContractID id) {
        return Address.wrap(Bytes.wrap(toEvmAddress(id)));
    }

    public static Address asTypedEvmAddress(final AccountID id) {
        return Address.wrap(Bytes.wrap(toEvmAddress(id)));
    }

    public static Address asTypedEvmAddress(final TokenID id) {
        return Address.wrap(Bytes.wrap(toEvmAddress(id)));
    }

    public static EntityId toEntityId(final com.hedera.hapi.node.base.AccountID accountID) {
        return EntityId.of(accountID.shardNum(), accountID.realmNum(), accountID.accountNum());
    }

    public static EntityId toEntityId(final com.hedera.hapi.node.base.ScheduleID scheduleID) {
        return EntityId.of(scheduleID.shardNum(), scheduleID.realmNum(), scheduleID.scheduleNum());
    }

    public static EntityId toEntityId(final com.hedera.hapi.node.base.TokenID tokenID) {
        return EntityId.of(tokenID.shardNum(), tokenID.realmNum(), tokenID.tokenNum());
    }

    public static EntityId toEntityId(final com.hedera.hapi.node.base.FileID fileID) {
        return EntityId.of(fileID.shardNum(), fileID.realmNum(), fileID.fileNum());
    }

    public static com.hedera.hapi.node.base.AccountID toAccountId(final Long id) {
        if (id == null) {
            return null;
        }
        final var decodedEntityId = EntityId.of(id);

        return toAccountId(decodedEntityId);
    }

    public static com.hedera.hapi.node.base.AccountID toAccountId(final long shard, final long realm, final long num) {
        final var decodedEntityId = EntityId.of(shard, realm, num);

        return toAccountId(decodedEntityId);
    }

    public static com.hedera.hapi.node.base.AccountID toAccountId(final Entity entity) {
        if (entity == null) {
            return com.hedera.hapi.node.base.AccountID.DEFAULT;
        }

        com.hedera.hapi.node.base.AccountID accountIdWithAlias = null;
        if (entity.getEvmAddress() != null && entity.getEvmAddress().length > 0) {
            accountIdWithAlias = toAccountId(entity.getShard(), entity.getRealm(), entity.getEvmAddress());
        } else if (entity.getAlias() != null && entity.getAlias().length > 0) {
            accountIdWithAlias = toAccountId(entity.getShard(), entity.getRealm(), entity.getAlias());
        }

        return accountIdWithAlias != null ? accountIdWithAlias : toAccountId(entity.toEntityId());
    }

    private static com.hedera.hapi.node.base.AccountID toAccountId(
            final Long shard, final Long realm, final byte[] alias) {
        return com.hedera.hapi.node.base.AccountID.newBuilder()
                .shardNum(shard)
                .realmNum(realm)
                .alias(com.hedera.pbj.runtime.io.buffer.Bytes.wrap(alias))
                .build();
    }

    public static com.hedera.hapi.node.base.AccountID toAccountId(final EntityId entityId) {
        if (entityId == null) {
            return null;
        }
        return com.hedera.hapi.node.base.AccountID.newBuilder()
                .shardNum(entityId.getShard())
                .realmNum(entityId.getRealm())
                .accountNum(entityId.getNum())
                .build();
    }

    public static com.hedera.hapi.node.base.AccountID toAccountId(final Long shard, final Long realm, final Long num) {
        return new com.hedera.hapi.node.base.AccountID(shard, realm, new OneOf<>(AccountOneOfType.ACCOUNT_NUM, num));
    }

    public static com.hedera.hapi.node.base.TokenID toTokenId(final Long entityId) {
        final var decodedEntityId = EntityId.of(entityId);

        return toTokenId(decodedEntityId);
    }

    public static com.hedera.hapi.node.base.TokenID toTokenId(final EntityId entityId) {
        if (entityId == null) {
            return null;
        }

        return com.hedera.hapi.node.base.TokenID.newBuilder()
                .shardNum(entityId.getShard())
                .realmNum(entityId.getRealm())
                .tokenNum(entityId.getNum())
                .build();
    }

    public static com.hedera.hapi.node.base.ContractID toContractID(final Address address) {
        var entity = fromEvmAddress(address.toArrayUnsafe());
        return entity == null
                ? null
                : com.hedera.hapi.node.base.ContractID.newBuilder()
                        .shardNum(entity.getShard())
                        .realmNum(entity.getRealm())
                        .contractNum(entity.getNum())
                        .build();
    }

    public static EntityId entityIdFromContractId(final com.hedera.hapi.node.base.ContractID id) {
        if (id == null || id.contractNum() == null) {
            return null;
        }
        return EntityId.of(id.shardNum(), id.realmNum(), id.contractNum());
    }
}
