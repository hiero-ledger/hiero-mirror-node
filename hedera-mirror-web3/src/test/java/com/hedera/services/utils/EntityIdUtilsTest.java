// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.utils;

import static com.hedera.services.utils.EntityIdUtils.asEvmAddress;
import static com.hedera.services.utils.EntityIdUtils.contractIdFromEvmAddress;
import static com.hedera.services.utils.EntityIdUtils.isOfEcdsaPublicAddressSize;
import static com.hedera.services.utils.EntityIdUtils.isOfEvmAddressSize;
import static com.hedera.services.utils.EntityIdUtils.parseAccount;
import static com.hedera.services.utils.EntityIdUtils.tokenIdFromEvmAddress;
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

@ExtendWith(MockitoExtension.class)
class EntityIdUtilsTest {

    public static final ByteString ECDSA_PUBLIC_KEY =
            ByteString.fromHex("3a2103af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d");
    public static final ByteString ECDSA_WRONG_PUBLIC_KEY =
            ByteString.fromHex("3a2103af80b90d145da28c583359beb47b217511e443e7a64dfdb27d");
    public static final ByteString EVM_ADDRESS = ByteString.fromHex("ebb9a1be370150759408cd7af48e9eda2b8ead57");
    public static final ByteString WRONG_EVM_ADDRESS = ByteString.fromHex("ebb9a1be3701cd7af48e9eda2b8ead57");

    private static final String EXPECTED_HEXED_ADDRESS = "0000000000000000000000000000000000000003";

    @Test
    void asSolidityAddressBytesWorksProperly() {
        final var id = AccountID.newBuilder()
                .setShardNum(1)
                .setRealmNum(2)
                .setAccountNum(3)
                .build();

        final var result = asEvmAddress(id);

        final var expectedBytes = new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3};

        assertArrayEquals(expectedBytes, result);
    }

    @Test
    void asSolidityAddressBytesFromToken() {
        final var id = TokenID.newBuilder()
                .setShardNum(1)
                .setRealmNum(2)
                .setTokenNum(3)
                .build();

        final var result = asEvmAddress(id);

        final var expectedBytes = new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3};

        assertArrayEquals(expectedBytes, result);
    }

    @Test
    void asContractWorks() {
        final var expected = ContractID.newBuilder()
                .setShardNum(1)
                .setRealmNum(2)
                .setContractNum(3)
                .build();
        final var id = AccountID.newBuilder()
                .setShardNum(1)
                .setRealmNum(2)
                .setAccountNum(3)
                .build();

        final var cid = asContract(id);

        assertEquals(expected, cid);
    }

    @Test
    void serializesExpectedSolidityAddress() {
        final byte[] shardBytes = {
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xAB,
        };
        final var shard = Ints.fromByteArray(shardBytes);
        final byte[] realmBytes = {
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xCD,
            (byte) 0xFE, (byte) 0x00, (byte) 0x00, (byte) 0xFE,
        };
        final var realm = Longs.fromByteArray(realmBytes);
        final byte[] numBytes = {
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xDE,
            (byte) 0xBA, (byte) 0x00, (byte) 0x00, (byte) 0xBA
        };
        final var num = Longs.fromByteArray(numBytes);
        final byte[] expected = {
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xDE,
            (byte) 0xBA, (byte) 0x00, (byte) 0x00, (byte) 0xBA
        };
        final var create2AddressBytes = Hex.decode("0102030405060708090a0b0c0d0e0f1011121314");
        final var equivAccount = asAccount(String.format("%d.%d.%d", shard, realm, num));
        final var equivContract = asContract(String.format("%d.%d.%d", shard, realm, num));
        final var equivToken = asToken(String.format("%d.%d.%d", shard, realm, num));
        final var create2Contract = ContractID.newBuilder()
                .setEvmAddress(ByteString.copyFrom(create2AddressBytes))
                .build();

        final var actual = asEvmAddress(num);
        final var typedActual = EntityIdUtils.asTypedEvmAddress(equivAccount);
        final var typedToken = EntityIdUtils.asTypedEvmAddress(equivToken);
        final var anotherActual = EntityIdUtils.asEvmAddress(equivContract);
        final var create2Actual = EntityIdUtils.asEvmAddress(create2Contract);
        final var actualHex = EntityIdUtils.asHexedEvmAddress(equivAccount);

        assertArrayEquals(expected, actual);
        assertArrayEquals(expected, anotherActual);
        assertArrayEquals(expected, typedActual.toArray());
        assertArrayEquals(expected, typedToken.toArray());
        assertArrayEquals(create2AddressBytes, create2Actual);
        assertEquals(CommonUtils.hex(expected), actualHex);
        assertEquals(asAccount(String.format("%d.%d.%d", 0, 0, num)), EntityIdUtils.accountIdFromEvmAddress(actual));
        assertEquals(asContract(String.format("%d.%d.%d", 0, 0, num)), contractIdFromEvmAddress(actual));
        assertEquals(asToken(String.format("%d.%d.%d", 0, 0, num)), tokenIdFromEvmAddress(actual));
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
        assertThat(EntityIdUtils.idFromEntityId(EntityId.of(1L, 2L, 3L)))
                .returns(1L, Id::shard)
                .returns(2L, Id::realm)
                .returns(3L, Id::num);
    }

    @Test
    void idFromEntityIdNullHandling() {
        assertThat(EntityIdUtils.idFromEntityId(null)).isNull();
    }

    @Test
    void isOfEvmAddressSizeWorks() {
        assertThat(isOfEvmAddressSize(EVM_ADDRESS)).isTrue();
        assertThat(isOfEvmAddressSize(WRONG_EVM_ADDRESS)).isFalse();
    }

    @Test
    void isOfEcdsaPublicAddressSizeWorks() {
        assertThat(isOfEcdsaPublicAddressSize(ECDSA_PUBLIC_KEY)).isTrue();
        assertThat(isOfEcdsaPublicAddressSize(ECDSA_WRONG_PUBLIC_KEY)).isFalse();
    }

    @Test
    void asSolidityAddressHexWorksProperly() {
        final var id = new Id(1, 2, 3);

        assertEquals(EXPECTED_HEXED_ADDRESS, EntityIdUtils.asHexedEvmAddress(id));
    }

    @Test
    void asSolidityAddressHexWorksProperlyForAccount() {
        final var accountId = AccountID.newBuilder()
                .setShardNum(1)
                .setRealmNum(2)
                .setAccountNum(3)
                .build();

        assertEquals(EXPECTED_HEXED_ADDRESS, EntityIdUtils.asHexedEvmAddress(accountId));
    }

    @Test
    void asSolidityAddressHexWorksProperlyForTokenId() {
        assertEquals(EXPECTED_HEXED_ADDRESS, EntityIdUtils.asHexedEvmAddress(3));
    }

    @Test
    void toEntityIdFromAccountId() {
        final var accountId = com.hedera.hapi.node.base.AccountID.newBuilder()
                .shardNum(1)
                .realmNum(2)
                .accountNum(3)
                .build();

        assertEquals(EntityId.of(1, 2, 3), EntityIdUtils.toEntityId(accountId));
    }

    @Test
    void toEntityIdFromFileId() {
        final var fileId = com.hedera.hapi.node.base.FileID.newBuilder()
                .shardNum(1)
                .realmNum(2)
                .fileNum(3)
                .build();
        assertEquals(EntityId.of(1, 2, 3), EntityIdUtils.toEntityId(fileId));
    }

    @Test
    void toAccountIdFromEntityId() {
        final var entityId = EntityId.of(1, 2, 3);

        final var expectedAccountId = com.hedera.hapi.node.base.AccountID.newBuilder()
                .shardNum(1)
                .realmNum(2)
                .accountNum(3)
                .build();
        assertEquals(expectedAccountId, EntityIdUtils.toAccountId(entityId));
    }

    @Test
    void toAccountIdFromId() {
        final var id = EntityId.of(1, 2, 3).getId();

        final var expectedAccountId = com.hedera.hapi.node.base.AccountID.newBuilder()
                .shardNum(1)
                .realmNum(2)
                .accountNum(3)
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
                .shardNum(1)
                .realmNum(2)
                .accountNum(3)
                .build();
        assertEquals(expectedAccountId, EntityIdUtils.toAccountId(1, 2, 3));
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
        final var id = EntityId.of(1, 2, 3).getId();

        final var expectedTokenId = com.hedera.hapi.node.base.TokenID.newBuilder()
                .shardNum(1)
                .realmNum(2)
                .tokenNum(3)
                .build();
        assertEquals(expectedTokenId, EntityIdUtils.toTokenId(id));
    }

    @Test
    void toTokenIdFromEntityId() {
        final var entityId = EntityId.of(1, 2, 3);

        final var expectedTokenId = com.hedera.hapi.node.base.TokenID.newBuilder()
                .shardNum(1)
                .realmNum(2)
                .tokenNum(3)
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
