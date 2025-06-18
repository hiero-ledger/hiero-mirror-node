// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.utils;

import static org.hiero.mirror.common.util.DomainUtils.fromEvmAddress;
import static org.hiero.mirror.common.util.DomainUtils.toEvmAddress;
import static org.hiero.mirror.web3.evm.account.AccountAccessorImpl.EVM_ADDRESS_SIZE;

import com.google.protobuf.ByteString;
import com.hedera.hapi.node.base.AccountID.AccountOneOfType;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.base.utility.CommonUtils;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.exception.InvalidEntityException;
import org.hyperledger.besu.datatypes.Address;

public final class EntityIdUtils {
    private static final String ENTITY_ID_FORMAT = "%d.%d.%d";

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

    public static TokenID tokenIdFromEvmAddress(final byte[] bytes) {
        var entityId = fromEvmAddress(bytes);

        return entityId == null ? TokenID.getDefaultInstance() : entityId.toTokenID();
    }

    public static TokenID tokenIdFromEvmAddress(final Address address) {
        return tokenIdFromEvmAddress(address.toArrayUnsafe());
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

    public static AccountID toGrpcAccountId(final int code) {
        var entityId = EntityId.of(code);

        return AccountID.newBuilder()
                .setShardNum(entityId.getShard())
                .setRealmNum(entityId.getRealm())
                .setAccountNum(entityId.getNum())
                .build();
    }

    public static AccountID toGrpcAccountId(final Id id) {
        return AccountID.newBuilder()
                .setShardNum(id.shard())
                .setRealmNum(id.realm())
                .setAccountNum(id.num())
                .build();
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

    public static com.hedera.hapi.node.base.FileID toFileId(final Long shard, final Long realm, final Long num) {
        return new com.hedera.hapi.node.base.FileID(shard, realm, num);
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

    public static Address toAddress(final com.hedera.pbj.runtime.io.buffer.Bytes bytes) {
        final var evmAddressBytes = bytes.toByteArray();
        return Address.wrap(org.apache.tuweni.bytes.Bytes.wrap(evmAddressBytes));
    }

    private static com.hedera.hapi.node.base.AccountID toAccountId(
            final Long shard, final Long realm, final byte[] alias) {
        return com.hedera.hapi.node.base.AccountID.newBuilder()
                .shardNum(shard)
                .realmNum(realm)
                .alias(com.hedera.pbj.runtime.io.buffer.Bytes.wrap(alias))
                .build();
    }

    public static String asHexedEvmAddress(final AccountID id) {
        return CommonUtils.hex(toEvmAddress(id));
    }

    public static String asHexedEvmAddress(final TokenID id) {
        return CommonUtils.hex(toEvmAddress(id));
    }

    public static String asHexedEvmAddress(final Id id) {
        return CommonUtils.hex(toEvmAddress(id.num()));
    }

    public static String asHexedEvmAddress(long id) {
        return CommonUtils.hex(toEvmAddress(EntityId.of(id)));
    }

    public static boolean isAlias(final AccountID idOrAlias) {
        return idOrAlias.getAccountNum() == 0 && !idOrAlias.getAlias().isEmpty();
    }

    public static EntityId entityIdFromId(Id id) {
        try {
            if (id == null) {
                return null;
            }
            return EntityId.of(id.shard(), id.realm(), id.num());
        } catch (InvalidEntityException e) {
            return EntityId.EMPTY;
        }
    }

    public static EntityId entityIdFromNftId(NftId id) {
        if (id == null) {
            return null;
        }
        return EntityId.of(id.shard(), id.realm(), id.num());
    }

    public static EntityId entityIdFromContractId(final com.hedera.hapi.node.base.ContractID id) {
        if (id == null || id.contractNum() == null) {
            return null;
        }
        return EntityId.of(id.shardNum(), id.realmNum(), id.contractNum());
    }

    public static Id idFromEncodedId(Long encodedId) {
        if (encodedId == null || encodedId == 0) {
            return null;
        }

        return idFromEntityId(EntityId.of(encodedId));
    }

    public static Id idFromEntityId(EntityId entityId) {
        if (entityId == null) {
            return null;
        }
        return new Id(entityId.getShard(), entityId.getRealm(), entityId.getNum());
    }

    public static String readableId(final Object o) {
        if (o instanceof Id id) {
            return String.format(ENTITY_ID_FORMAT, id.shard(), id.realm(), id.num());
        }
        if (o instanceof AccountID id) {
            return String.format(ENTITY_ID_FORMAT, id.getShardNum(), id.getRealmNum(), id.getAccountNum());
        }
        if (o instanceof TokenID id) {
            return String.format(ENTITY_ID_FORMAT, id.getShardNum(), id.getRealmNum(), id.getTokenNum());
        }
        if (o instanceof NftID id) {
            final var tokenID = id.getTokenID();
            return String.format(
                    ENTITY_ID_FORMAT + ".%d",
                    tokenID.getShardNum(),
                    tokenID.getRealmNum(),
                    tokenID.getTokenNum(),
                    id.getSerialNumber());
        }
        return String.valueOf(o);
    }

    public static boolean isAliasSizeGreaterThanEvmAddress(final ByteString alias) {
        return alias.size() > EVM_ADDRESS_SIZE;
    }
}
