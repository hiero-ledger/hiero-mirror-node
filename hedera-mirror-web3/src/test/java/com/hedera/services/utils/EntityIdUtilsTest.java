// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.utils;

import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;
import static com.hedera.services.utils.EntityIdUtils.*;
import static com.hedera.services.utils.IdUtils.asAccount;
import static com.hedera.services.utils.IdUtils.asContract;
import static com.hedera.services.utils.IdUtils.asToken;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;
import com.hedera.hapi.node.base.AccountID.AccountOneOfType;
import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.common.utility.CommonUtils;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.datatypes.Address;
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

    private static final String EXPECTED_HEXED_ADDRESS = "000001ff000000000000ffff0000003fffffffff";
    private static final EntityId ENTITY_ID = EntityId.of(Long.MAX_VALUE);
    private static final long ID = ENTITY_ID.getId();
    private static final long SHARD = ENTITY_ID.getShard();
    private static final long REALM = ENTITY_ID.getRealm();
    private static final long NUM = ENTITY_ID.getNum();

    @Test
    void asContractWorks() {
        final var expected = ContractID.newBuilder()
                .setShardNum(SHARD)
                .setRealmNum(REALM)
                .setContractNum(NUM)
                .build();
        final var id = AccountID.newBuilder()
                .setShardNum(SHARD)
                .setRealmNum(REALM)
                .setAccountNum(NUM)
                .build();

        final var cid = asContract(id);

        assertEquals(expected, cid);
    }

    @Test
    void serializesExpectedSolidityAddress() {
        final byte[] shardBytes = Ints.toByteArray((int) SHARD);
        final byte[] realmBytes = Longs.toByteArray(REALM);
        final byte[] numBytes = Longs.toByteArray(NUM);
        final byte[] expected = ArrayUtils.addAll(ArrayUtils.addAll(shardBytes, realmBytes), numBytes);

        final var create2AddressBytes = Hex.decode("0102030405060708090a0b0c0d0e0f1011121314");
        final var equivAccount = asAccount(String.format("%d.%d.%d", SHARD, REALM, NUM));
        final var equivContract = asContract(String.format("%d.%d.%d", SHARD, REALM, NUM));
        final var equivToken = asToken(String.format("%d.%d.%d", SHARD, REALM, NUM));
        final var create2Contract = ContractID.newBuilder()
                .setEvmAddress(ByteString.copyFrom(create2AddressBytes))
                .build();

        final var actual = toEvmAddress(ID);
        final var typedActual = EntityIdUtils.asTypedEvmAddress(equivAccount);
        final var typedToken = EntityIdUtils.asTypedEvmAddress(equivToken);
        final var typedContract = EntityIdUtils.asTypedEvmAddress(equivContract);

        final var anotherActual = toEvmAddress(equivContract);
        final var create2Actual = toEvmAddress(create2Contract);
        final var actualHex = EntityIdUtils.asHexedEvmAddress(equivAccount);

        assertArrayEquals(expected, actual);
        assertArrayEquals(expected, anotherActual);
        assertArrayEquals(expected, typedActual.toArray());
        assertArrayEquals(expected, typedToken.toArray());
        assertArrayEquals(expected, typedContract.toArray());
        assertArrayEquals(create2AddressBytes, create2Actual);
        assertEquals(CommonUtils.hex(expected), actualHex);
        assertEquals(equivAccount, accountIdFromEvmAddress(actual));
        assertEquals(equivContract, contractIdFromEvmAddress(actual));
        assertEquals(equivToken, tokenIdFromEvmAddress(actual));
    }

    @Test
    void fromAddressToIdNonLongZeroAlias() {
        var evmAddress = ArrayUtils.addAll(
                ArrayUtils.addAll(Ints.toByteArray(Integer.MAX_VALUE), Longs.toByteArray(Long.MAX_VALUE)),
                Longs.toByteArray(Long.MAX_VALUE));

        var address = Address.fromHexString(CommonUtils.hex(evmAddress));

        assertEquals(TokenID.getDefaultInstance(), tokenIdFromEvmAddress(evmAddress));
        assertEquals(TokenID.getDefaultInstance(), tokenIdFromEvmAddress(address));
        assertEquals(ContractID.getDefaultInstance(), contractIdFromEvmAddress(evmAddress));
        assertEquals(ContractID.getDefaultInstance(), contractIdFromEvmAddress(address));
        assertEquals(AccountID.getDefaultInstance(), accountIdFromEvmAddress(evmAddress));
        assertEquals(AccountID.getDefaultInstance(), accountIdFromEvmAddress(address));
    }

    @ParameterizedTest
    @CsvSource({"1.0.0", "0.1.0", "0.0.1", "1.2.3"})
    void parsesValidLiteral(final String goodLiteral) {
        assertEquals(asAccount(goodLiteral), parseAccount(goodLiteral));
    }

    @ParameterizedTest
    @CsvSource({"asdf", "notANumber"})
    void parsesNonValidLiteral(final String badLiteral) {
        assertThrows(IllegalArgumentException.class, () -> parseAccount(badLiteral));
    }

    @Test
    void entityIdFromId() {
        assertThat(EntityIdUtils.entityIdFromId(new Id(1L, 2L, 3L)))
                .returns(1L, EntityId::getShard)
                .returns(2L, EntityId::getRealm)
                .returns(3L, EntityId::getNum);
    }

    @Test
    void entityIdFromIdNullHandling() {
        assertThat(EntityIdUtils.entityIdFromId(null)).isNull();
    }

    @Test
    void entityIdFromContractIdNullContractId() {
        assertThat(EntityIdUtils.entityIdFromContractId(null)).isNull();
    }

    @Test
    void idFromEntityId() {
        assertThat(EntityIdUtils.idFromEntityId(EntityId.of(SHARD, REALM, NUM)))
                .returns(SHARD, Id::shard)
                .returns(REALM, Id::realm)
                .returns(NUM, Id::num);
    }

    @Test
    void idFromEntityIdNullHandling() {
        assertThat(EntityIdUtils.idFromEntityId(null)).isNull();
    }

    @Test
    void asSolidityAddressHexWorksProperly() {
        final var id = new Id(SHARD, REALM, NUM);

        assertEquals(EXPECTED_HEXED_ADDRESS, EntityIdUtils.asHexedEvmAddress(id));
    }

    @Test
    void asSolidityAddressHexWorksProperlyForAccount() {
        final var accountId = AccountID.newBuilder()
                .setShardNum(SHARD)
                .setRealmNum(REALM)
                .setAccountNum(NUM)
                .build();

        assertEquals(EXPECTED_HEXED_ADDRESS, EntityIdUtils.asHexedEvmAddress(accountId));
    }

    @Test
    void asSolidityAddressHexWorksProperlyForTokenId() {
        assertEquals(EXPECTED_HEXED_ADDRESS, EntityIdUtils.asHexedEvmAddress(ID));
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
    void toFileIdFromShardRealmNum() {
        final var expectedFileId = com.hedera.hapi.node.base.FileID.newBuilder()
                .shardNum(1)
                .realmNum(2)
                .fileNum(3)
                .build();
        assertEquals(expectedFileId, EntityIdUtils.toFileId(1L, 2L, 3L));
    }

    @Test
    void toTokenIdFromId() {
        final var id = EntityId.of(SHARD, REALM, NUM).getId();

        final var expectedTokenId = com.hedera.hapi.node.base.TokenID.newBuilder()
                .shardNum(SHARD)
                .realmNum(REALM)
                .tokenNum(NUM)
                .build();
        assertEquals(expectedTokenId, EntityIdUtils.toTokenId(id));
    }

    @Test
    void toTokenIdFromEntityId() {
        final var entityId = EntityId.of(SHARD, REALM, NUM);

        final var expectedTokenId = com.hedera.hapi.node.base.TokenID.newBuilder()
                .shardNum(SHARD)
                .realmNum(REALM)
                .tokenNum(NUM)
                .build();
        assertEquals(expectedTokenId, EntityIdUtils.toTokenId(entityId));
    }

    @Test
    void toAddressFromPbjBytes() {
        final var address = Address.fromHexString("0x0000000000000000000000000000000000000001");
        final var pbjBytes = Bytes.fromHex("0000000000000000000000000000000000000001");

        assertEquals(address, EntityIdUtils.toAddress(pbjBytes));
    }
}
