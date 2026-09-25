// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import java.util.Objects;
import org.hiero.mirror.common.util.SignatureUtils;
import org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1;

/**
 * Substitutes consensus node's EthSigsUtils methods that use native Besu libraries with Java-based implementations
 * so that it works in a native image. Every method that touches {@code LibSecp256k1} must be substituted or deleted:
 * that class loads a JNA native library from its static initializer.
 */
@TargetClass(className = "com.hedera.node.app.hapi.utils.EthSigsUtils")
final class EthSigsUtilsSubstitute {

    @Substitute
    public static byte[] recoverAddressFromPrivateKey(final byte[] privateKeyBytes) {
        return SignatureUtils.recoverAddressFromPrivateKey(privateKeyBytes);
    }

    @Substitute
    public static byte[] recoverAddressFromPubKey(final byte[] pubKeyBytes) {
        return SignatureUtils.recoverAddressFromPubKey(pubKeyBytes);
    }

    @Substitute
    public static Bytes recoverAddressFromPubKey(final Bytes pubKeyBytes) {
        Objects.requireNonNull(pubKeyBytes);
        return Bytes.wrap(SignatureUtils.recoverAddressFromPubKey(pubKeyBytes.toByteArray()));
    }

    @Delete
    public static byte[] recoverAddressFromPubKey(final LibSecp256k1.secp256k1_pubkey pubKey) {
        return null;
    }
}
