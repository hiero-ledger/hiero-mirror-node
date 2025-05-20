// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.node.app.service.evm.utils.EthSigsUtils;
import java.security.KeyPairGenerator;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.web3j.generated.HRC632Contract;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Keys;

class ContractCallIsAuthorizedTest extends AbstractContractCallServiceTest {
    private static final byte[] MESSAGE_HASH = new Keccak.Digest256().digest("messageString".getBytes());
    private static final byte[] DIFFERENT_HASH = new Keccak.Digest256().digest("differentMessage".getBytes());

    @Test
    void isAuthorizedRawECDSA() throws Exception {
        // Given
        // Generate new key pair
        final var keyPair = Keys.createEcKeyPair();
        var publicKey = getProtobufKeyECDSA(keyPair.getPublicKey());
        var privateKey = keyPair.getPrivateKey().toByteArray();
        // Sign the message hash with the private key
        final var signedMessage = signMessageECDSA(MESSAGE_HASH, privateKey);
        // Recover the EVM address from the private key and persist account with that address and public key
        final var addressBytes = EthSigsUtils.recoverAddressFromPrivateKey(privateKey);
        persistAccountWithEvmAddressAndPublicKey(addressBytes, publicKey);
        // When
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        final var result = contract.call_isAuthorizedRawCall(
                asHeadlongAddress(addressBytes).toString(), MESSAGE_HASH, signedMessage);
        final var functionCall = contract.send_isAuthorizedRawCall(
                asHeadlongAddress(addressBytes).toString(), MESSAGE_HASH, signedMessage);
        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            assertThat(result.send()).isTrue();
            verifyEthCallAndEstimateGas(functionCall, contract);
        } else {
            assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        }
    }

    @Test
    void isAuthorizedRawECDSADifferentHash() throws Exception {
        // Given
        // Generate new key pair
        final var keyPair = Keys.createEcKeyPair();
        var publicKey = getProtobufKeyECDSA(keyPair.getPublicKey());
        var privateKey = keyPair.getPrivateKey().toByteArray();
        // Sign the message hash with the private key
        final var signedMessage = signMessageECDSA(MESSAGE_HASH, privateKey);
        // Recover the EVM address from the private key and persist account with that address and public key
        final var addressBytes = EthSigsUtils.recoverAddressFromPrivateKey(privateKey);
        persistAccountWithEvmAddressAndPublicKey(addressBytes, publicKey);
        // When
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        final var result = contract.call_isAuthorizedRawCall(
                asHeadlongAddress(addressBytes).toString(), DIFFERENT_HASH, signedMessage);
        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            assertThat(result.send()).isFalse();
        } else {
            assertThrows(MirrorEvmTransactionException.class, result::send);
        }
    }

    @Test
    void isAuthorizedRawECDSAInvalidSignedValue() throws Exception {
        // Given
        // Generate new key pair
        final var keyPair = Keys.createEcKeyPair();
        var publicKey = getProtobufKeyECDSA(keyPair.getPublicKey());
        var privateKey = keyPair.getPrivateKey().toByteArray();
        // Sign the message hash with the private key
        final var signedMessage = signMessageECDSA(MESSAGE_HASH, privateKey);
        // Get the EVM address from the private key
        final var addressBytes = EthSigsUtils.recoverAddressFromPrivateKey(privateKey);
        persistAccountWithEvmAddressAndPublicKey(addressBytes, publicKey);

        // Set the last byte of the signed message to an invalid value
        signedMessage[signedMessage.length - 1] = (byte) 2;
        // When
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        final var result = contract.call_isAuthorizedRawCall(
                asHeadlongAddress(addressBytes).toString(), MESSAGE_HASH, signedMessage);
        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            assertThat(result.send()).isFalse();
        } else {
            assertThrows(MirrorEvmTransactionException.class, result::send);
        }
    }

    @Test
    void isAuthorizedRawECDSAInvalidSignatureLength() throws Exception {
        // Given
        final byte[] invalidSignature = new byte[64];
        // Generate new key pair
        final var keyPair = Keys.createEcKeyPair();
        var publicKey = getProtobufKeyECDSA(keyPair.getPublicKey());
        // Recover the EVM address from the private key and persist account with that address and public key
        final var addressBytes = EthSigsUtils.recoverAddressFromPubKey(publicKey);
        persistAccountWithEvmAddressAndPublicKey(addressBytes, publicKey);
        // When
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        final var result = contract.call_isAuthorizedRawCall(
                asHeadlongAddress(addressBytes).toString(), MESSAGE_HASH, invalidSignature);
        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            final var exception = assertThrows(MirrorEvmTransactionException.class, result::send);
            // Then
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        } else {
            assertThrows(MirrorEvmTransactionException.class, result::send);
        }
    }

    @Test
    void isAuthorizedRawED25519() throws Exception {
        // Given
        // Generate new key pair
        final var keyPairGenerator = KeyPairGenerator.getInstance(ED_25519);
        final var keyPair = keyPairGenerator.generateKeyPair();
        var publicKey = keyPair.getPublic();
        var publicProtoKey = getProtobufKeyEd25519(publicKey);
        var privateKey = keyPair.getPrivate();
        // Sign the message hash with the private key
        final var signedBytes = signBytesED25519(MESSAGE_HASH, privateKey);
        // Persist account with private key and no EVM address. This is needed in order to perform a contract call using
        // the long zero address.
        var accountEntity = persistAccountWithEvmAddressAndPublicKey(null, publicProtoKey);
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        // When
        final var result =
                contract.call_isAuthorizedRawCall(getAddressFromEntity(accountEntity), MESSAGE_HASH, signedBytes);
        final var functionCall =
                contract.send_isAuthorizedRawCall(getAddressFromEntity(accountEntity), MESSAGE_HASH, signedBytes);
        // then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            assertThat(result.send()).isTrue();
            verifyEthCallAndEstimateGas(functionCall, contract);
        } else {
            assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        }
    }

    @Test
    void isAuthorizedRawED25519DifferentHash() throws Exception {
        // Given
        // Generate new key pair
        final var keyPairGenerator = KeyPairGenerator.getInstance(ED_25519);
        final var keyPair = keyPairGenerator.generateKeyPair();

        var publicKey = keyPair.getPublic();
        var publicProtoKey = getProtobufKeyEd25519(publicKey);
        var privateKey = keyPair.getPrivate();
        // Sign the message hash with the private key
        final var signedBytes = signBytesED25519(MESSAGE_HASH, privateKey);
        // Persist account with private key and no EVM address. This is needed in order to perform a contract call using
        // the long zero address.

        var accountEntity = persistAccountWithEvmAddressAndPublicKey(null, publicProtoKey);
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        // When
        final var result =
                contract.call_isAuthorizedRawCall(getAddressFromEntity(accountEntity), DIFFERENT_HASH, signedBytes);
        final var functionCall =
                contract.send_isAuthorizedRawCall(getAddressFromEntity(accountEntity), DIFFERENT_HASH, signedBytes);
        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            assertThat(result.send()).isFalse();
        } else {
            assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        }
    }

    @Test
    void isAuthorizedRawED25519InvalidSignedValue() throws Exception {
        // Given
        // Generate new key pair
        final var keyPairGenerator = KeyPairGenerator.getInstance(ED_25519);
        final var keyPair = keyPairGenerator.generateKeyPair();
        var publicKey = keyPair.getPublic();
        var publicProtoKey = getProtobufKeyEd25519(publicKey);
        // Persist account with private key and no EVM address. This is needed in order to perform a contract call using
        // the long zero address.
        var accountEntity = persistAccountWithEvmAddressAndPublicKey(null, publicProtoKey);
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        // When
        final var result =
                contract.call_isAuthorizedRawCall(getAddressFromEntity(accountEntity), MESSAGE_HASH, new byte[65]);
        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            final var exception = assertThrows(MirrorEvmTransactionException.class, result::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        } else {
            assertThrows(MirrorEvmTransactionException.class, result::send);
        }
    }

    @Test
    void isAuthorizedRawECDSAKeyWighLongZero() throws Exception {
        // Given
        // Generate new key pair
        final var keyPair = Keys.createEcKeyPair();
        var publicKey = getProtobufKeyECDSA(keyPair.getPublicKey());
        var privateKey = keyPair.getPrivateKey().toByteArray();
        // Sign the message hash with the private key
        final var signedMessage = signMessageECDSA(MESSAGE_HASH, privateKey);

        final var addressBytes = EthSigsUtils.recoverAddressFromPrivateKey(privateKey);
        var accountEntity = persistAccountWithEvmAddressAndPublicKey(addressBytes, publicKey);

        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        final var result =
                contract.call_isAuthorizedRawCall(getAddressFromEntity(accountEntity), MESSAGE_HASH, signedMessage);

        if (mirrorNodeEvmProperties.isModularizedServices()) {
            final var exception = assertThrows(MirrorEvmTransactionException.class, result::send);
            // Then
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        } else {
            assertThrows(MirrorEvmTransactionException.class, result::send);
        }
    }
}
