// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import static org.hiero.mirror.common.util.SignatureUtils.EC_DOMAIN_PARAMETERS;
import static org.hiero.mirror.common.util.SignatureUtils.recoverAddressFromPubKey;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.node.app.hapi.utils.ethereum.EthTxSigs;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import java.math.BigInteger;
import lombok.experimental.UtilityClass;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.crypto.SECPSignature;

/**
 * Native-image replacement for EthTxSigs' JNA secp256k1 calls (parse_compact + recover + serialize),
 * reimplemented via Besu's native agnostic abstraction layer. JNA direct mapping doesn't link under SubstrateVM.
 */
@UtilityClass
final class EthTxSigsSubstitute {

    @VisibleForTesting
    static final SECP256K1 SECP256K1 = new SECP256K1();

    static EthTxSigs recoverPublicKey(int recoveryId, byte[] r, byte[] s, byte[] message) {
        final var messageHash = Bytes32.wrap(new Keccak.Digest256().digest(message));
        final var signature = new SECPSignature(new BigInteger(1, r), new BigInteger(1, s), (byte) recoveryId);
        final var publicKey =
                SECP256K1.recoverPublicKeyFromSignature(messageHash, signature).orElseThrow();
        final var compressedKey = publicKey.asEcPoint(EC_DOMAIN_PARAMETERS).getEncoded(true);
        final var address = recoverAddressFromPubKey(compressedKey);
        return new EthTxSigs(compressedKey, address);
    }

    // Target classes cannot have state, so we use this inner class as a workaround
    @TargetClass(className = "com.hedera.node.app.hapi.utils.ethereum.EthTxSigs")
    static final class Target {
        @Substitute
        public static EthTxSigs extractSignatures(EthTxData ethTx) {
            final var message = EthTxSigs.calculateSignableMessage(ethTx);
            return recoverPublicKey(ethTx.recId(), ethTx.r(), ethTx.s(), message);
        }
    }
}
