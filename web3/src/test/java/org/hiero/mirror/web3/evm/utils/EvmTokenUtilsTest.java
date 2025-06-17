// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.utils;

import static com.hedera.services.utils.EntityIdUtils.asHexedEvmAddress;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.util.DomainUtils;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;

class EvmTokenUtilsTest {
    private static final DomainBuilder domainBuilder = new DomainBuilder();
    private static final Address EMPTY_ADDRESS = Address.wrap(Bytes.wrap(new byte[20]));

    @Test
    void toAddress() {
        var contractId = domainBuilder.entityId().toContractID();
        byte[] contractAddress = DomainUtils.toEvmAddress(contractId);

        assertThat(EvmTokenUtils.toAddress(contractId).toArrayUnsafe()).isEqualTo(contractAddress);
        assertThat(EvmTokenUtils.toAddress(EntityId.of(contractId).getId()).toArrayUnsafe())
                .isEqualTo(contractAddress);

        var accountId = domainBuilder.entityId();
        byte[] accountAddress = DomainUtils.toEvmAddress(accountId);

        assertThat(EvmTokenUtils.toAddress(accountId).toArrayUnsafe()).isEqualTo(accountAddress);
        assertThat(EvmTokenUtils.toAddress(accountId.getId()).toArrayUnsafe()).isEqualTo(accountAddress);
    }

    @Test
    void evmKeyWithEcdsa() throws InvalidProtocolBufferException, DecoderException {
        // hexed value of a serialized Key with ecdsa algorithm
        final var ecdsaBytes = Hex.decodeHex("3a21ccd4f651636406f8a2a9902a2a604be1fb480dba6591ff4d992f8a6bc6abc137c7");

        final var result = EvmTokenUtils.evmKey(ecdsaBytes);

        assertThat(result.getECDSASecp256K1()).isNotEmpty();
        assertThat(result.getEd25519()).isEmpty();
        assertThat(result.getContractId()).isEqualTo(EMPTY_ADDRESS);
        assertThat(result.getDelegatableContractId()).isEqualTo(EMPTY_ADDRESS);
    }

    @Test
    void evmKeyWithEd25519() throws InvalidProtocolBufferException, DecoderException {
        // hexed value of a serialized Key with ed25519 algorithm
        final var keyWithEd25519 =
                Hex.decodeHex("1220000038746a20d630ceb81a24bd43798159108ec144e185c1c60a5e39fb933e2a");

        final var result = EvmTokenUtils.evmKey(keyWithEd25519);

        assertThat(result.getECDSASecp256K1()).isEmpty();
        assertThat(result.getEd25519()).isNotEmpty();
        assertThat(result.getContractId()).isEqualTo(EMPTY_ADDRESS);
        assertThat(result.getDelegatableContractId()).isEqualTo(EMPTY_ADDRESS);
    }

    @Test
    void evmKeyWithContractId() throws InvalidProtocolBufferException, DecoderException {
        // hexed value of a serialized Key with contractId
        final var keyWithContractId = Hex.decodeHex("0a070801100118c409");
        final var contractAddress = Address.fromHexString("0x00000000000000000000000000000000000004c4");

        final var result = EvmTokenUtils.evmKey(keyWithContractId);

        assertThat(result.getECDSASecp256K1()).isEmpty();
        assertThat(result.getEd25519()).isEmpty();
        assertThat(result.getContractId()).isEqualTo(contractAddress);
        assertThat(result.getDelegatableContractId()).isEqualTo(EMPTY_ADDRESS);
    }

    @Test
    void evmKeyWithDelegateContractId() throws InvalidProtocolBufferException, DecoderException {
        // hexed value of a serialized Key with delegate contractId
        final var keyWithDelegateContractId = Hex.decodeHex("420318c509");
        final var delegateContractAddress = Address.fromHexString("0x00000000000000000000000000000000000004c5");

        final var result = EvmTokenUtils.evmKey(keyWithDelegateContractId);

        assertThat(result.getECDSASecp256K1()).isEmpty();
        assertThat(result.getEd25519()).isEmpty();
        assertThat(result.getDelegatableContractId()).isEqualTo(delegateContractAddress);
        assertThat(result.getContractId()).isEqualTo(EMPTY_ADDRESS);
    }

    @Test
    void evmKeyWithInvalidBytesLength() {
        assertThatThrownBy(() -> EvmTokenUtils.evmKey(new byte[36])).isInstanceOf(InvalidProtocolBufferException.class);
    }

    @Test
    void emptyEvmKeyForNull() throws InvalidProtocolBufferException {
        final var result = EvmTokenUtils.evmKey(null);

        assertThat(result.getEd25519()).isEmpty();
        assertThat(result.getECDSASecp256K1()).isEmpty();
        assertThat(result.getContractId()).isEqualTo(Address.ZERO);
        assertThat(result.getDelegatableContractId()).isEqualTo(Address.ZERO);
    }

    @Test
    void entityIdNumFromAddress() {
        final var entityId = domainBuilder.entityId();
        final var contractAddress = Address.fromHexString(asHexedEvmAddress(entityId.getNum()));
        assertThat(EvmTokenUtils.entityIdNumFromEvmAddress(contractAddress)).isEqualTo(entityId.getId());
    }

    @Test
    void entityIdFromAddress() {
        final var entityId = domainBuilder.entityId();
        final var contractAddress = Address.fromHexString(asHexedEvmAddress(entityId.getNum()));

        assertThat(EvmTokenUtils.entityIdFromEvmAddress(contractAddress)).isEqualTo(entityId);
    }

    @Test
    void entityIdNumFromEthAddress() {
        final var ethAddress = Address.fromHexString("0x23f5e49569a835d7bf9aefd30e4f60cdd570f225");
        assertThat(EvmTokenUtils.entityIdNumFromEvmAddress(ethAddress)).isZero();
    }

    @Test
    void entityIdFromEthAddress() {
        final var ethAddress = Address.fromHexString("0x23f5e49569a835d7bf9aefd30e4f60cdd570f225");
        assertThat(EvmTokenUtils.entityIdFromEvmAddress(ethAddress)).isNull();
    }

    @Test
    void entityIdFromEmptyAddress() {
        var entityId = domainBuilder.entityNum(0L);
        assertThat(EvmTokenUtils.entityIdFromEvmAddress(EMPTY_ADDRESS)).isEqualTo(entityId);
    }
}
