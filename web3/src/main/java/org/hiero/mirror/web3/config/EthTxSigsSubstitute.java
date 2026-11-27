// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import static org.hiero.mirror.common.util.SignatureUtils.EC_DOMAIN_PARAMETERS;
import static org.hiero.mirror.common.util.SignatureUtils.keccak256;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.node.app.hapi.utils.ethereum.CodeDelegation;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.node.app.hapi.utils.ethereum.EthTxSigs;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Optional;
import lombok.CustomLog;
import lombok.experimental.UtilityClass;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.crypto.SECPPublicKey;
import org.hyperledger.besu.crypto.SECPSignature;

/**
 * Native-image replacement for EthTxSigs' libsecp256k1 calls (parse_compact + recover + serialize), reimplemented via
 * Besu's SECP256K1 abstraction. The Java fallback of Besu's recovery accepts a wider input set than libsecp256k1 (e.g.
 * r in [N, P), any recovery id), so the input validation of {@code EthTxSigs.extractSig} is reproduced here before
 * recovery, and every failure is reported as an {@link IllegalArgumentException} just like the replaced code.
 */
@CustomLog
@UtilityClass
final class EthTxSigsSubstitute {

    @VisibleForTesting
    static final SECP256K1 SECP256K1 = new SECP256K1();

    private static final String COULD_NOT_RECOVER = "Could not recover signature";
    private static final BigInteger CURVE_N = EC_DOMAIN_PARAMETERS.getN();
    // Upper bound (exclusive) for s, identical to EthTxSigs.HALF_N
    private static final BigInteger HALF_N = CURVE_N.shiftRight(1);
    private static final int SCALAR_BYTES = 32;

    /**
     * Recovers the signer of {@code message}, accepting exactly the inputs {@code EthTxSigs.extractSig} accepts.
     *
     * @throws IllegalArgumentException if r is outside [1, N), s is outside [1, N/2) or the key cannot be recovered
     */
    static EthTxSigs recoverPublicKey(final int recoveryId, final byte[] r, final byte[] s, final byte[] message) {
        // Only the parity is meaningful, even if the high order bits encode the chain id
        final byte recId = (byte) Math.floorMod(recoveryId, 2);
        var rValue = parseInBounds(r, CURVE_N);
        var sValue = parseInBounds(s, HALF_N);

        if (r.length > SCALAR_BYTES || s.length > SCALAR_BYTES) {
            // Zero-padded values: EthTxSigs.concatLeftPadded throws for a long r and lets a long s overwrite r
            final var padded = concatLeftPadded(r, s);
            rValue = new BigInteger(1, padded, 0, SCALAR_BYTES);
            sValue = new BigInteger(1, padded, SCALAR_BYTES, SCALAR_BYTES);
        }

        final var messageHash = Bytes32.wrap(keccak256(message));
        final var publicKey = recover(messageHash, rValue, sValue, recId);

        // Derive both outputs from the raw x || y bytes rather than round-tripping through EC points
        final var compressedKey = new byte[SCALAR_BYTES + 1];
        compressedKey[0] = (byte) (0x02 | (publicKey[publicKey.length - 1] & 1));
        System.arraycopy(publicKey, 0, compressedKey, 1, SCALAR_BYTES);
        final var address = Arrays.copyOfRange(keccak256(publicKey), 12, SCALAR_BYTES);
        return new EthTxSigs(compressedKey, address);
    }

    // Same left-padding as EthTxSigs.concatLeftPadded, including its quirks for values longer than 32 bytes
    private static byte[] concatLeftPadded(final byte[] r, final byte[] s) {
        final var signature = new byte[SCALAR_BYTES * 2];
        System.arraycopy(r, 0, signature, SCALAR_BYTES - r.length, r.length);
        System.arraycopy(s, 0, signature, SCALAR_BYTES * 2 - s.length, s.length);
        return signature;
    }

    private static BigInteger parseInBounds(final byte[] value, final BigInteger upperBound) {
        final var integer = new BigInteger(1, value);
        if (integer.signum() == 0) {
            throw new IllegalArgumentException("Curve point must be >= 1");
        }
        if (integer.compareTo(upperBound) >= 0) {
            throw new IllegalArgumentException("Curve point must be < N");
        }
        return integer;
    }

    /** Returns the recovered public key as the 64-byte x || y encoding. */
    private static byte[] recover(final Bytes32 messageHash, final BigInteger r, final BigInteger s, final byte recId) {
        final Optional<SECPPublicKey> publicKey;
        try {
            final var signature = SECPSignature.create(r, s, recId, CURVE_N);
            publicKey = SECP256K1.recoverPublicKeyFromSignature(messageHash, signature);
        } catch (RuntimeException e) {
            // Besu's Java path throws on an r with no curve point; its native path throws on an empty result
            throw new IllegalArgumentException(COULD_NOT_RECOVER, e);
        }
        return publicKey
                .orElseThrow(() -> new IllegalArgumentException(COULD_NOT_RECOVER))
                .getEncodedBytes()
                .toArrayUnsafe();
    }

    // Target classes cannot have state, so we use this inner class as a workaround
    @TargetClass(className = "com.hedera.node.app.hapi.utils.ethereum.EthTxSigs")
    static final class Target {
        @Substitute
        public static EthTxSigs extractSignatures(EthTxData ethTx) {
            final var message = EthTxSigs.calculateSignableMessage(ethTx);
            return recoverPublicKey(ethTx.recId(), ethTx.r(), ethTx.s(), message);
        }

        @Substitute
        public static Optional<EthTxSigs> extractAuthoritySignature(final CodeDelegation codeDelegation) {
            try {
                final var message = codeDelegation.calculateSignableMessage();
                return Optional.of(
                        recoverPublicKey(codeDelegation.yParity(), codeDelegation.r(), codeDelegation.s(), message));
            } catch (RuntimeException e) {
                log.warn("Exception thrown extracting code delegation authority signatures!", e);
                return Optional.empty();
            }
        }
    }
}
