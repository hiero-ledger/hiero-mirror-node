// SPDX-License-Identifier: Apache-2.0

package org.hiero.base.crypto.engine;

import static com.swirlds.logging.legacy.LogMarker.TESTING_EXCEPTIONS;
import static org.hiero.base.utility.CommonUtils.hex;

import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.NamedParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hiero.base.crypto.SignatureType;
import org.hiero.base.crypto.TransactionSignature;

/**
 * Shadow base-crypto class from hedera app to prevent eager initialization of SodiumJava
 * Implementation of an Ed25519 signature verification provider.
 * This implementation only supports Ed25519 signatures
 * and uses the built-in JDK Ed25519 support introduced in Java 15 (JEP 339).
 */
public class Ed25519VerificationProvider
        extends OperationProvider<TransactionSignature, Void, Boolean, Signature, SignatureType> {

    private static final Logger logger = LogManager.getLogger(Ed25519VerificationProvider.class);

    private static final String ALGORITHM = NamedParameterSpec.ED25519.getName();

    /**
     * ASN.1 DER prefix for an Ed25519 SubjectPublicKeyInfo structure. Prepending this to a raw 32-byte Ed25519 public
     * key produces a valid X.509/DER-encoded key accepted by the JDK's {@link X509EncodedKeySpec}.
     * <p>
     * Breakdown: 30 2a        — SEQUENCE, 42 bytes 30 05        — SEQUENCE, 5 bytes (AlgorithmIdentifier) 06 03 — OID,
     * 3 bytes 2b 65 70     — OID 1.3.101.112 (id-EdDSA / Ed25519) 03 21        — BIT STRING, 33 bytes 00 — 0 unused
     * bits (followed by the 32 raw key bytes)
     */
    private static final byte[] ED25519_DER_PREFIX = {
        0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
    };

    /**
     * Encodes a raw 32-byte Ed25519 public key into a DER/X.509 format by prepending the Ed25519 ASN.1
     * SubjectPublicKeyInfo header, making it consumable by {@link X509EncodedKeySpec}.
     *
     * @param rawPublicKey the raw 32-byte Ed25519 public key
     * @return a DER-encoded byte array suitable for use with {@link X509EncodedKeySpec}
     */
    private static byte[] toDerEncodedPublicKey(final byte[] rawPublicKey) {
        final byte[] derEncoded = new byte[ED25519_DER_PREFIX.length + rawPublicKey.length];
        System.arraycopy(ED25519_DER_PREFIX, 0, derEncoded, 0, ED25519_DER_PREFIX.length);
        System.arraycopy(rawPublicKey, 0, derEncoded, ED25519_DER_PREFIX.length, rawPublicKey.length);
        return derEncoded;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the {@link Signature} instance for Ed25519.
     */
    @Override
    protected Signature loadAlgorithm(final SignatureType algorithmType) {
        try {
            return Signature.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Ed25519 algorithm not available in this JDK (requires Java 15+)", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Boolean handleItem(
            final Signature algorithm,
            final SignatureType algorithmType,
            final TransactionSignature sig,
            final Void optionalData) {
        return compute(
                algorithm,
                algorithmType,
                sig.getMessage().toByteArray(),
                sig.getSignature().toByteArray(),
                sig.getPublicKey().toByteArray());
    }

    /**
     * Computes the result of the cryptographic transformation using the provided item and algorithm.
     *
     * @param algorithm     the thread-local JDK {@link Signature} instance for Ed25519
     * @param algorithmType the type of algorithm to be used when performing the transformation
     * @param message       the original message that was signed
     * @param signature     the signature to be verified
     * @param publicKey     the raw 32-byte Ed25519 public key
     * @return true if the provided signature is valid; false otherwise
     */
    private boolean compute(
            final Signature algorithm,
            final SignatureType algorithmType,
            final byte[] message,
            final byte[] signature,
            final byte[] publicKey) {
        try {
            final var keySpec = new X509EncodedKeySpec(toDerEncodedPublicKey(publicKey));
            final var pub = KeyFactory.getInstance(ALGORITHM).generatePublic(keySpec);
            algorithm.initVerify(pub);
            algorithm.update(message);
            return algorithm.verify(signature);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Ed25519 algorithm not available in this JDK (requires Java 15+)", e);
        } catch (InvalidKeySpecException | InvalidKeyException | SignatureException e) {
            if (logger.isDebugEnabled()) {
                logger.debug(
                        TESTING_EXCEPTIONS.getMarker(),
                        "Adv Crypto Subsystem: Signature Verification Failure for signature type {} [ publicKey = {}, "
                                + "signature = {} ]",
                        algorithmType,
                        hex(publicKey),
                        hex(signature));
            }
            return false;
        }
    }
}
