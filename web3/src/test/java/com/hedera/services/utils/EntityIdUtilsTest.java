// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.utils;

import static com.hedera.services.utils.EntityIdUtils.accountIdFromEvmAddress;
import static com.hedera.services.utils.EntityIdUtils.contractIdFromEvmAddress;
import static com.hedera.services.utils.EntityIdUtils.toTokenId;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.toEvmAddress;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;
import com.hedera.hapi.node.base.AccountID.AccountOneOfType;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import org.hiero.base.utility.CommonUtils;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testcontainers.shaded.org.apache.commons.lang3.ArrayUtils;

@ExtendWith(MockitoExtension.class)
class EntityIdUtilsTest {

    public static final ByteString ECDSA_PUBLIC_KEY =
            ByteString.fromHex("3a2103af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d");
    public static final ByteString ECDSA_WRONG_PUBLIC_KEY =
            ByteString.fromHex("3a2103af80b90d145da28c583359beb47b217511e443e7a64dfdb27d");
    public static final ByteString EVM_ADDRESS = ByteString.fromHex("ebb9a1be370150759408cd7af48e9eda2b8ead57");
    public static final ByteString WRONG_EVM_ADDRESS = ByteString.fromHex("ebb9a1be3701cd7af48e9eda2b8ead57");

    private static final String EXPECTED_HEXED_ADDRESS = "0000000000000000000000000000003fffffffff";
    private static final EntityId ENTITY_ID = EntityId.of(Long.MAX_VALUE);
    private static final long ID = ENTITY_ID.getId();
    private static final long SHARD = ENTITY_ID.getShard();
    private static final long REALM = ENTITY_ID.getRealm();
    private static final long NUM = ENTITY_ID.getNum();

    private static final CommonProperties COMMON_PROPERTIES = CommonProperties.getInstance();

    @BeforeEach
    void setUp() {
        COMMON_PROPERTIES.setShard(SHARD);
        COMMON_PROPERTIES.setRealm(REALM);
    }

    @Test
    void fromAddressToIdNonLongZeroAlias() {
        var evmAddress = ArrayUtils.addAll(
                ArrayUtils.addAll(Ints.toByteArray(Integer.MAX_VALUE), Longs.toByteArray(Long.MAX_VALUE)),
                Longs.toByteArray(Long.MAX_VALUE));

        var address = Address.fromHexString(CommonUtils.hex(evmAddress));

        assertEquals(ContractID.getDefaultInstance(), contractIdFromEvmAddress(evmAddress));
        assertEquals(ContractID.getDefaultInstance(), contractIdFromEvmAddress(address));
        assertEquals(AccountID.getDefaultInstance(), accountIdFromEvmAddress(evmAddress));
        assertEquals(AccountID.getDefaultInstance(), accountIdFromEvmAddress(address));
    }

    @ParameterizedTest
    @CsvSource({"asdf", "notANumber"})
    void parsesNonValidLiteral(final String badLiteral) {
        assertThrows(
                IllegalArgumentException.class, () -> EntityId.of(badLiteral).toAccountID());
    }

    @Test
    void entityIdFromContractIdNullContractId() {
        assertThat(EntityIdUtils.entityIdFromContractId(null)).isNull();
    }

    @Test
    void asSolidityAddressHexWorksProperlyForEncodedId() {
        assertEquals(EXPECTED_HEXED_ADDRESS, CommonUtils.hex(toEvmAddress(EntityId.of(ID))));
    }

    @Test
    void toEntityIdFromAccountId() {
        final var accountId = com.hedera.hapi.node.base.AccountID.newBuilder()
                .shardNum(SHARD)
                .realmNum(REALM)
                .accountNum(NUM)
                .build();

        assertEquals(EntityId.of(SHARD, REALM, NUM), EntityIdUtils.toEntityId(accountId));
    }

    @Test
    void toEntityIdFromScheduleId() {
        final var scheduleId = com.hedera.hapi.node.base.ScheduleID.newBuilder()
                .shardNum(SHARD)
                .realmNum(REALM)
                .scheduleNum(NUM)
                .build();

        assertEquals(EntityId.of(SHARD, REALM, NUM), EntityIdUtils.toEntityId(scheduleId));
    }

    @Test
    void toEntityIdFromFileId() {
        final var fileId = com.hedera.hapi.node.base.FileID.newBuilder()
                .shardNum(SHARD)
                .realmNum(REALM)
                .fileNum(NUM)
                .build();
        assertEquals(EntityId.of(SHARD, REALM, NUM), EntityIdUtils.toEntityId(fileId));
    }

    @Test
    void toAccountIdFromEntityId() {
        final var entityId = EntityId.of(SHARD, REALM, NUM);

        final var expectedAccountId = com.hedera.hapi.node.base.AccountID.newBuilder()
                .shardNum(SHARD)
                .realmNum(REALM)
                .accountNum(NUM)
                .build();
        assertEquals(expectedAccountId, EntityIdUtils.toAccountId(entityId));
    }

    @Test
    void toAccountIdFromId() {
        final var id = EntityId.of(SHARD, REALM, NUM).getId();

        final var expectedAccountId = com.hedera.hapi.node.base.AccountID.newBuilder()
                .shardNum(SHARD)
                .realmNum(REALM)
                .accountNum(NUM)
                .build();
        assertEquals(expectedAccountId, EntityIdUtils.toAccountId(id));
    }

    @Test
    void toAccountIdFromNullId() {
        assertThat(EntityIdUtils.toAccountId((Long) null)).isNull();
    }

    @Test
    void toAccountIdFromShardRealmNum() {
        final var expectedAccountId = com.hedera.hapi.node.base.AccountID.newBuilder()
                .shardNum(SHARD)
                .realmNum(REALM)
                .accountNum(NUM)
                .build();
        assertEquals(expectedAccountId, EntityIdUtils.toAccountId(SHARD, REALM, NUM));
    }

    @Test
    void toAccountIdFromEntityWithNoAlias() {
        final var domainBuilder = new DomainBuilder();
        final var entity = domainBuilder.entity().get();
        entity.setEvmAddress(null);
        entity.setAlias(null);

        final var expectedAccountId = com.hedera.hapi.node.base.AccountID.newBuilder()
                .shardNum(entity.getShard())
                .realmNum(entity.getRealm())
                .accountNum(entity.getNum())
                .build();
        assertEquals(expectedAccountId, EntityIdUtils.toAccountId(entity));
    }

    @Test
    void toAccountIdWithNullEntity() {
        final var accountId = EntityIdUtils.toAccountId((Entity) null);

        assertThat(accountId).isEqualTo(com.hedera.hapi.node.base.AccountID.DEFAULT);
    }

    @Test
    void toAccountIdFromEntityWithEvmAddress() {
        final var domainBuilder = new DomainBuilder();
        final var entity = domainBuilder.entity().get();

        final var expectedAccountId = com.hedera.hapi.node.base.AccountID.newBuilder()
                .shardNum(entity.getShard())
                .realmNum(entity.getRealm())
                .alias(Bytes.wrap(entity.getEvmAddress()))
                .build();
        assertEquals(expectedAccountId, EntityIdUtils.toAccountId(entity));
    }

    @Test
    void toAccountIdFromEntityWithAlias() {
        final var domainBuilder = new DomainBuilder();
        final var entity = domainBuilder.entity().get();
        entity.setEvmAddress(null);

        final var expectedAccountId = com.hedera.hapi.node.base.AccountID.newBuilder()
                .shardNum(entity.getShard())
                .realmNum(entity.getRealm())
                .alias(Bytes.wrap(entity.getAlias()))
                .build();
        assertEquals(expectedAccountId, EntityIdUtils.toAccountId(entity));
    }

    @Test
    void toAccountIdWithShardRealmAndNum() {
        final long shard = 0L;
        final long realm = 0L;
        final long num = 10L;

        final var accountId = EntityIdUtils.toAccountId(shard, realm, num);
        final var expectedAccountId =
                new com.hedera.hapi.node.base.AccountID(shard, realm, new OneOf<>(AccountOneOfType.ACCOUNT_NUM, num));

        assertEquals(expectedAccountId, accountId);
    }

    @Test
    void toTokenIdFromId() {
        final var id = EntityId.of(SHARD, REALM, NUM).getId();

        final var expectedTokenId = com.hedera.hapi.node.base.TokenID.newBuilder()
                .shardNum(SHARD)
                .realmNum(REALM)
                .tokenNum(NUM)
                .build();
        assertEquals(expectedTokenId, toTokenId(id));
    }

    @Test
    void toTokenIdFromNullShouldReturnNull() {
        EntityId entityId = null;
        assertNull(toTokenId(entityId));
    }

    @Test
    void toTokenIdFromEntityId() {
        final var entityId = EntityId.of(SHARD, REALM, NUM);

        final var expectedTokenId = com.hedera.hapi.node.base.TokenID.newBuilder()
                .shardNum(SHARD)
                .realmNum(REALM)
                .tokenNum(NUM)
                .build();
        assertEquals(expectedTokenId, toTokenId(entityId));
    }
}
