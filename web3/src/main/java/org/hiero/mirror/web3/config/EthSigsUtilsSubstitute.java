// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import org.hiero.mirror.common.util.SignatureUtils;

/**
 * Substitutes consensus node's EthSigsUtils methods that use native Besu libraries with Java-based implementations
 * so that it works in a native image.
 */
@TargetClass(className = "com.hedera.node.app.hapi.utils.EthSigsUtils")
final class EthSigsUtilsSubstitute {

    @Substitute
    public static byte[] recoverAddressFromPubKey(final byte[] pubKeyBytes) {
        return SignatureUtils.recoverAddressFromPubKey(pubKeyBytes);
    }
}
