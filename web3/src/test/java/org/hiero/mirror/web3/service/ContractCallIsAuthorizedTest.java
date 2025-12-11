// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties.ALLOW_LONG_ZERO_ADDRESSES;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.node.app.service.evm.utils.EthSigsUtils;
import java.math.BigInteger;
import java.security.KeyPairGenerator;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.web3j.generated.HRC632Contract;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.web3j.crypto.Keys;

class ContractCallIsAuthorizedTest extends AbstractContractCallServiceTest {
    private static final byte[] MESSAGE_HASH = new Keccak.Digest256().digest("messageString".getBytes());
    private static final byte[] DIFFERENT_HASH = new Keccak.Digest256().digest("differentMessage".getBytes());

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "Native secp256k1 DLL not available on Windows")
    void isAuthorizedRawECDSA(boolean longZeroAddressAllowed) throws Exception {
        // Given

        // Use hardcoded keys, since generating random pairs cause flakiness in EcdsaSecp256k1Verifier
        var publicKey = getProtobufKeyECDSA(
                new BigInteger(
                        "11788470961158488135883467201924341153027947433918562205169190496120219037602889961374831714736506359301656521475956907842267525572988131983910815995309671"));
        var privateKey = new BigInteger("31210558703497683178602097010844366970220898241500850671032577535463882585633")
                .toByteArray();

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
        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(longZeroAddressAllowed));

        // Then
        assertThat(result.send()).isTrue();
        verifyEthCallAndEstimateGas(functionCall, contract);

        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(false));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "Native secp256k1 DLL not available on Windows")
    void isAuthorizedRawECDSADifferentHash(boolean longZeroAddressAllowed) throws Exception {
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
        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(longZeroAddressAllowed));

        // Then
        assertThat(result.send()).isFalse();

        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(false));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "Native secp256k1 DLL not available on Windows")
    void isAuthorizedRawECDSAInvalidSignedValue(boolean longZeroAddressAllowed) throws Exception {
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

        // Set a byte at the end of the signed message to an invalid value. It should be at least byte before the last,
        // since the very last byte gets truncated during execution
        final var valueToChange = signedMessage[signedMessage.length - 2];
        byte newValue;
        if (valueToChange < 0) {
            newValue = (byte) 1;
        } else {
            newValue = (byte) -1;
        }

        signedMessage[signedMessage.length - 2] = newValue;

        // When
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        final var result = contract.call_isAuthorizedRawCall(
                asHeadlongAddress(addressBytes).toString(), MESSAGE_HASH, signedMessage);
        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(longZeroAddressAllowed));

        // Then
        assertThat(result.send()).isFalse();

        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(false));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "Native secp256k1 DLL not available on Windows")
    void isAuthorizedRawECDSAInvalidSignatureLength(boolean longZeroAddressAllowed) throws Exception {
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
        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(longZeroAddressAllowed));

        // Then
        final var exception = assertThrows(MirrorEvmTransactionException.class, result::send);
        // Then
        assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());

        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(false));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void isAuthorizedRawED25519(boolean longZeroAddressAllowed) throws Exception {
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
        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(longZeroAddressAllowed));

        // then
        assertThat(result.send()).isTrue();
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void isAuthorizedRawED25519DifferentHash(boolean longZeroAddressAllowed) throws Exception {
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
        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(longZeroAddressAllowed));

        // Then
        assertThat(result.send()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void isAuthorizedRawED25519InvalidSignedValue(boolean longZeroAddressAllowed) throws Exception {
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
        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(longZeroAddressAllowed));

        // Then
        final var exception = assertThrows(MirrorEvmTransactionException.class, result::send);
        assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "Native secp256k1 DLL not available on Windows")
    void isAuthorizedRawECDSAKeyWighLongZero(boolean longZeroAddressAllowed) throws Exception {
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
        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(longZeroAddressAllowed));

        final var exception = assertThrows(MirrorEvmTransactionException.class, result::send);
        // Then
        assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
    }
}
