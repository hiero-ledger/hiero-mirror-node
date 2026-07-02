// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import static com.hedera.node.app.hapi.utils.ethereum.EthTxSigs.calculateSignableMessage;
import static com.hedera.node.app.hapi.utils.ethereum.EthTxSigs.extractSignatures;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData.EthTransactionType;
import java.math.BigInteger;
import org.hiero.mirror.common.util.SignatureUtils;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Sign;

final class EthTxSigsSubstituteTest {

    @Test
    void recoverCompressedPubKeyFromSigMatchesNative() throws Exception {
        final var keyPair = ECKeyPair.create(BigInteger.valueOf(1234567890L));
        final var to = Address.fromHexString("0x742d35Cc6634C0532925a3b844Bc454e4438f44e")
                .toArray();
        final var callData = new byte[] {0x12, 0x34};
        final var chainId = new byte[] {0x01};
        final var gasPrice = new byte[] {0x01};
        final var nonce = 0;
        final var gasLimit = 21_000;
        final var value = BigInteger.valueOf(1000);

        final var message = calculateSignableMessage(new EthTxData(
                new byte[0],
                EthTransactionType.EIP2930,
                chainId,
                nonce,
                gasPrice,
                null,
                null,
                gasLimit,
                to,
                value,
                callData,
                new byte[] {},
                new Object[0],
                0,
                null,
                new byte[] {},
                new byte[] {}));
        final var hash = Hash.sha3(message);
        final var signature = Sign.signMessage(hash, keyPair, false);
        final var recId = signature.getV()[0] - 27;

        final var ethTx = new EthTxData(
                new byte[0],
                EthTransactionType.EIP2930,
                chainId,
                nonce,
                gasPrice,
                null,
                null,
                gasLimit,
                to,
                value,
                callData,
                new byte[] {},
                new Object[0],
                recId,
                null,
                signature.getR(),
                signature.getS());

        final var nativeSigs = extractSignatures(ethTx);
        final var messageBytes = calculateSignableMessage(ethTx);
        final var javaCompressedKey =
                SignatureUtils.recoverCompressedPubKeyFromSig(ethTx.recId(), ethTx.r(), ethTx.s(), messageBytes);
        final var javaAddress = SignatureUtils.recoverAddressFromPubKey(javaCompressedKey);

        assertThat(javaCompressedKey).isEqualTo(nativeSigs.publicKey());
        assertThat(javaAddress).isEqualTo(nativeSigs.address());
    }
}
