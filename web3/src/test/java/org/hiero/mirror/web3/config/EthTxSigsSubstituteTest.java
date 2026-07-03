// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.util.SignatureUtils.EC_DOMAIN_PARAMETERS;

import java.math.BigInteger;
import java.util.HexFormat;
import lombok.SneakyThrows;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.hiero.mirror.common.util.SignatureUtils;
import org.hyperledger.besu.crypto.SECPPrivateKey;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class EthTxSigsSubstituteTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void recoverPublicKey(boolean useNative) {
        // Given
        useNative(useNative);
        final var privateKeyHex = "c85ef7d79691fe79573b1a7064c19c1a9819ebdbd1faaab1a8ec92344438aaf4";
        final var privateKey = new BigInteger(1, HexFormat.of().parseHex(privateKeyHex));
        final var message = "Hello world".getBytes();
        final var expected = EC_DOMAIN_PARAMETERS.getG().multiply(privateKey).normalize();
        final var signature = sign(privateKey, message);
        final var r = signature.getR().toByteArray();
        final var s = signature.getS().toByteArray();
        final byte recoveryId = signature.getRecId();

        // When
        final var recovered = EthTxSigsSubstitute.recoverPublicKey(recoveryId, r, s, message);

        // Then
        assertThat(recovered.publicKey()).isEqualTo(expected.getEncoded(true));
        assertThat(recovered.address())
                .as("recovering the address from the recovered key matches the address from the true key")
                .isEqualTo(SignatureUtils.recoverAddressFromPubKey(expected.getEncoded(true)))
                .asHexString()
                .isEqualToIgnoringCase("CD2A3D9F938E13CD947EC05ABC7FE734DF8DD826");
    }

    static void useNative(boolean useNative) {
        if (useNative) {
            EthTxSigsSubstitute.SECP256K1.maybeEnableNative();
        } else {
            EthTxSigsSubstitute.SECP256K1.disableNative();
        }
    }

    @SneakyThrows
    private static SECPSignature sign(final BigInteger privateKeyInteger, final byte[] message) {
        final var hash = Bytes32.wrap(new Keccak.Digest256().digest(message));
        final var privateKey = SECPPrivateKey.create(privateKeyInteger, SignatureAlgorithm.ALGORITHM);
        final var keyPair = EthTxSigsSubstitute.SECP256K1.createKeyPair(privateKey);
        return EthTxSigsSubstitute.SECP256K1.sign(hash, keyPair);
    }
}
