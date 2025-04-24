// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.service;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1.CONTEXT;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.web3j.generated.HRC632Contract;
import com.hedera.node.app.service.evm.utils.EthSigsUtils;
import com.hederahashgraph.api.proto.java.Key;
import com.sun.jna.ptr.IntByReference;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.jcajce.provider.digest.SHA384.Digest;
import org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

class ContractCallIsAuthorizedTest extends AbstractContractCallServiceTest {

    @Test
    void isAuthorizedRawECDSA() throws Exception {
        final var messageString = "message";
        final var messageHash = new Keccak.Digest256().digest(messageString.getBytes());

        var keyPair = Keys.createEcKeyPair();
        var publicKey = getProtobufKeyECDSA(keyPair.getPublicKey());
        var privateKey = keyPair.getPrivateKey().toByteArray();
        final var signedMessage = signMessageECDSA(messageHash, privateKey);

        final var evmAddress = Bytes.wrap(EthSigsUtils.recoverAddressFromPubKey(
                        ByteString.copyFrom(publicKey).substring(2).toByteArray()))
                .toArray();

        var accountEntity = persistAccountWithEvmAddressAndPublicKey(evmAddress, publicKey);

        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        final var result = contract.call_isAuthorizedRawCall(
                getAddressFromEvmAddress(accountEntity.getEvmAddress()), messageHash, signedMessage);
        final var functionCall = contract.send_isAuthorizedRawCall(
                getAddressFromEvmAddress(accountEntity.getEvmAddress()), messageHash, signedMessage);

        if (mirrorNodeEvmProperties.isModularizedServices()) {
            assertThat(result.send()).isTrue();
            verifyEthCallAndEstimateGas(functionCall, contract);
        } else {
            assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        }
    }

    @Test
    void isAuthorizedRawECDSADifferentHash() throws Exception {
        final var messageHash = new Keccak.Digest256().digest("messageString".getBytes());
        final var differentHash = new Keccak.Digest256().digest("messageString1".getBytes());

        var keyPair = Keys.createEcKeyPair();
        var publicKey = getProtobufKeyECDSA(keyPair.getPublicKey());
        var privateKey = keyPair.getPrivateKey().toByteArray();
        final var signedMessage = signMessageECDSA(messageHash, privateKey);
        final var evmAddress = Bytes.wrap(EthSigsUtils.recoverAddressFromPubKey(
                        ByteString.copyFrom(publicKey).substring(2).toByteArray()))
                .toArray();

        var accountEntity = persistAccountWithEvmAddressAndPublicKey(evmAddress, publicKey);

        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        final var result = contract.call_isAuthorizedRawCall(
                getAddressFromEvmAddress(accountEntity.getEvmAddress()), differentHash, signedMessage);

        if (mirrorNodeEvmProperties.isModularizedServices()) {
            assertThat(result.send()).isFalse();
        } else {
            assertThrows(MirrorEvmTransactionException.class, result::send);
        }
    }

    @Test
    void isAuthorizedRawECDSAInvalidSignedValue() throws Exception {
        // Given
        final var messageHash = new Keccak.Digest256().digest("messageString".getBytes());
        // Generate new key pair
        var keyPair = Keys.createEcKeyPair();
        var publicKey = getProtobufKeyECDSA(keyPair.getPublicKey());
        var privateKey = keyPair.getPrivateKey().toByteArray();
        // Sign the message hash with the private key
        final var signedMessage = signMessageECDSA(messageHash, privateKey);
        // Get the EVM address from the public key
        final var evmAddress = Bytes.wrap(EthSigsUtils.recoverAddressFromPubKey(
                        ByteString.copyFrom(publicKey).substring(2).toByteArray()))
                .toArray();

        // Persist account entity with specific EVM address and public key
        var accountEntity = persistAccountWithEvmAddressAndPublicKey(evmAddress, publicKey);

        // Set the last byte of the signed message to an invalid value
        signedMessage[signedMessage.length - 1] = (byte) 2;
        // When
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        final var result = contract.call_isAuthorizedRawCall(
                getAddressFromEvmAddress(accountEntity.getEvmAddress()), messageHash, signedMessage);
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
        final var messageHash = new Keccak.Digest256().digest("messageString".getBytes());
        final byte[] invalidSignature = new byte[64];
        // Generate new key pair
        var keyPair = Keys.createEcKeyPair();
        var publicKey = getProtobufKeyECDSA(keyPair.getPublicKey());
        // Get the EVM address from the public key
        final var evmAddress = Bytes.wrap(EthSigsUtils.recoverAddressFromPubKey(
                        ByteString.copyFrom(publicKey).substring(2).toByteArray()))
                .toArray();
        // Persist account entity with specific EVM address and public key
        var accountEntity = persistAccountWithEvmAddressAndPublicKey(evmAddress, publicKey);

        // When
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        final var result =
                contract.call_isAuthorizedRawCall(getAddressFromEvmAddress(evmAddress), messageHash, invalidSignature);
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
        final var messageString = "message";
        final var messageHash = new Keccak.Digest256().digest(messageString.getBytes());

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("Ed25519");
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        var publicKey = keyPair.getPublic();
        var publicProtoKey = getProtobufKeyEd25519(publicKey);
        var privateKey = keyPair.getPrivate();

        final var signedBytes = signBytesED25519(messageHash, privateKey);
        var accountEntity = persistAccountWithEvmAddressAndPublicKey(null, publicProtoKey);

        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        final var result =
                contract.call_isAuthorizedRawCall(getAddressFromEntity(accountEntity), messageHash, signedBytes);
        final var functionCall =
                contract.send_isAuthorizedRawCall(getAddressFromEntity(accountEntity), messageHash, signedBytes);

        if (mirrorNodeEvmProperties.isModularizedServices()) {
            assertThat(result.send()).isTrue();
            verifyEthCallAndEstimateGas(functionCall, contract);
        } else {
            assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        }
    }

    @Test
    void isAuthorizedRawED25519DifferentHash() throws Exception {
        final var messageHash = new Keccak.Digest256().digest("message".getBytes());
        final var differentHash = new Digest().digest("message1".getBytes());

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("Ed25519");
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        var publicKey = keyPair.getPublic();
        var publicProtoKey = getProtobufKeyEd25519(publicKey);
        var privateKey = keyPair.getPrivate();

        final var signedBytes = signBytesED25519(messageHash, privateKey);

        var accountEntity = persistAccountWithEvmAddressAndPublicKey(null, publicProtoKey);

        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        final var result =
                contract.call_isAuthorizedRawCall(getAddressFromEntity(accountEntity), differentHash, signedBytes);
        final var functionCall =
                contract.send_isAuthorizedRawCall(getAddressFromEntity(accountEntity), differentHash, signedBytes);

        if (mirrorNodeEvmProperties.isModularizedServices()) {
            assertThat(result.send()).isFalse();
        } else {
            assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        }
    }

    @Test
    void isAuthorizedRawED25519InvalidSignedValue() throws Exception {
        final var messageHash = new Keccak.Digest256().digest("message".getBytes());

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("Ed25519");
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        var publicKey = keyPair.getPublic();
        var publicProtoKey = getProtobufKeyEd25519(publicKey);

        var accountEntity = persistAccountWithEvmAddressAndPublicKey(null, publicProtoKey);

        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        final var result =
                contract.call_isAuthorizedRawCall(getAddressFromEntity(accountEntity), messageHash, new byte[65]);

        if (mirrorNodeEvmProperties.isModularizedServices()) {
            final var exception = assertThrows(MirrorEvmTransactionException.class, result::send);
            // Then
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        } else {
            assertThrows(MirrorEvmTransactionException.class, result::send);
        }
    }

    @Test
    void isAuthorizedRawECDSAKeyWighLongZero() throws Exception {
        final var messageString = "message";
        final var messageHash = new Keccak.Digest256().digest(messageString.getBytes());

        var keyPair = Keys.createEcKeyPair();
        var publicKey = getProtobufKeyECDSA(keyPair.getPublicKey());
        var privateKey = keyPair.getPrivateKey().toByteArray();
        final var signedMessage = signMessageECDSA(messageHash, privateKey);

        final var evmAddress = Bytes.wrap(EthSigsUtils.recoverAddressFromPubKey(
                        ByteString.copyFrom(publicKey).substring(2).toByteArray()))
                .toArray();

        var accountEntity = persistAccountWithEvmAddressAndPublicKey(evmAddress, publicKey);

        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        final var result =
                contract.call_isAuthorizedRawCall(getAddressFromEntity(accountEntity), messageHash, signedMessage);

        if (mirrorNodeEvmProperties.isModularizedServices()) {
            final var exception = assertThrows(MirrorEvmTransactionException.class, result::send);
            // Then
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        } else {
            assertThrows(MirrorEvmTransactionException.class, result::send);
        }
    }

    private byte[] getProtobufKeyEd25519(PublicKey publicKey) {
        var publicKeyEncoded = publicKey.getEncoded();
        SubjectPublicKeyInfo info = SubjectPublicKeyInfo.getInstance(publicKeyEncoded);
        var rawEd25519 = info.getPublicKeyData().getOctets();
        // wrap in Proto
        ByteString keyByteString = ByteString.copyFrom(rawEd25519);
        return Key.newBuilder().setEd25519(keyByteString).build().toByteArray();
    }

    private byte[] getProtobufKeyECDSA(BigInteger publicKey) {
        // Convert BigInteger public key to a full 65-byte uncompressed key
        var fullPublicKey = Numeric.hexStringToByteArray(Numeric.toHexStringWithPrefixZeroPadded(publicKey, 130));
        // Convert to compressed format (33 bytes)
        var prefix = (byte) (fullPublicKey[64] % 2 == 0 ? 0x02 : 0x03); // 0x02 for even Y, 0x03 for odd Y
        var compressedKey = new byte[33];
        compressedKey[0] = prefix;
        System.arraycopy(fullPublicKey, 1, compressedKey, 1, 32); // Copy only X coordinate
        var finalResult = ByteString.copyFrom(compressedKey);
        return Key.newBuilder().setECDSASecp256K1(finalResult).build().toByteArray();
    }

    private Entity persistAccountWithEvmAddressAndPublicKey(byte[] evmAddress, byte[] publicKey) {
        return accountEntityPersistCustomizable(e -> e.alias(evmAddress)
                .evmAddress(evmAddress)
                .key(publicKey)
                .type(EntityType.ACCOUNT)
                .balance(DEFAULT_ACCOUNT_BALANCE));
    }

    public static byte[] signMessageECDSA(final byte[] messageHash, byte[] privateKey) {
        final LibSecp256k1.secp256k1_ecdsa_recoverable_signature signature =
                new LibSecp256k1.secp256k1_ecdsa_recoverable_signature();
        LibSecp256k1.secp256k1_ecdsa_sign_recoverable(CONTEXT, signature, messageHash, privateKey, null, null);

        final ByteBuffer compactSig = ByteBuffer.allocate(64);
        final IntByReference recId = new IntByReference(0);
        LibSecp256k1.secp256k1_ecdsa_recoverable_signature_serialize_compact(
                LibSecp256k1.CONTEXT, compactSig, recId, signature);
        compactSig.flip();
        final byte[] sig = compactSig.array();

        final byte[] result = new byte[65];
        System.arraycopy(sig, 0, result, 0, 64);
        result[64] = (byte) (recId.getValue() + 27);
        return result;
    }

    public static byte[] signBytesED25519(final byte[] msg, final PrivateKey privateKey)
            throws InvalidKeyException, SignatureException, NoSuchAlgorithmException {
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(privateKey);
        signature.update(msg);
        return signature.sign();
    }
}
