// SPDX-License-Identifier: Apache-2.0

package org.hiero.base.crypto.engine;

import java.security.Signature;
import org.hiero.base.crypto.SignatureType;
import org.hiero.base.crypto.TransactionSignature;

/**
 * Shadow base-crypto class from hedera app to prevent eager initialization of SodiumJava
 */
public class Ed25519VerificationProvider
        extends OperationProvider<TransactionSignature, Void, Boolean, Signature, SignatureType> {

    @Override
    protected Signature loadAlgorithm(final SignatureType algorithmType) {
        throw new IllegalStateException("This feature is not implemented");
    }

    @Override
    protected Boolean handleItem(
            final Signature algorithm,
            final SignatureType algorithmType,
            final TransactionSignature sig,
            final Void optionalData) {
        throw new IllegalStateException("This feature is not implemented");
    }
}
