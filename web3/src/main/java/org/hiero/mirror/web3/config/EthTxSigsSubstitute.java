// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.node.app.hapi.utils.ethereum.EthTxSigs;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import org.hiero.mirror.common.util.SignatureUtils;

/**
 * Substitutes consensus node's EthTxSigs methods that use native Besu libraries with Java-based
 * implementations so that it works in a native image.
 */
@TargetClass(className = "com.hedera.node.app.hapi.utils.ethereum.EthTxSigs")
final class EthTxSigsSubstitute {

    @Substitute
    public static EthTxSigs extractSignatures(EthTxData ethTx) {
        final var message = EthTxSigs.calculateSignableMessage(ethTx);
        final var compressedKey =
                SignatureUtils.recoverCompressedPubKeyFromSig(ethTx.recId(), ethTx.r(), ethTx.s(), message);
        final var address = SignatureUtils.recoverAddressFromPubKey(compressedKey);
        return new EthTxSigs(compressedKey, address);
    }
}
