// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.util;

import java.math.BigInteger;
import java.util.Arrays;
import lombok.experimental.UtilityClass;
import org.bouncycastle.asn1.x9.X9IntegerConverter;
import org.bouncycastle.crypto.digests.KeccakDigest;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.math.ec.ECAlgorithms;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.math.ec.custom.sec.SecP256K1Curve;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
@UtilityClass
public final class SignatureUtils {

    public static final int ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH = 33;
    private static final ECDomainParameters EC_DOMAIN_PARAMETERS;
    private static final BigInteger CURVE_N;

    static {
        final var curveParams = CustomNamedCurves.getByName("secp256k1");
        EC_DOMAIN_PARAMETERS = new ECDomainParameters(
                curveParams.getCurve(), curveParams.getG(), curveParams.getN(), curveParams.getH());
        CURVE_N = EC_DOMAIN_PARAMETERS.getN();
    }

    /**
     * Recovers a compressed secp256k1 public key from an Ethereum transaction signature.
     *
     * @param recId the recovery id
     * @param r the r component of the signature
     * @param s the s component of the signature
     * @param message the signable message bytes
     * @return the 33-byte compressed public key
     */
    public static byte[] recoverCompressedPubKeyFromSig(int recId, byte[] r, byte[] s, byte[] message) {
        recId = Math.floorMod(recId, 2);
        final var dataHash = keccak256(message);
        checkSignatureComponentInBounds(r);
        checkSignatureComponentInBounds(s);

        final var recovered = recoverFromSignature(
                recId, new BigInteger(1, leftPadTo32Bytes(r)), new BigInteger(1, leftPadTo32Bytes(s)), dataHash);
        if (recovered == null) {
            throw new IllegalArgumentException("Could not recover signature");
        }

        return recovered.getEncoded(true);
    }

    /**
     * Converts an ECDSA secp256k1 key to a 20-byte EVM address by taking the keccak hash of it.
     *
     * @param publicKeyBytes The bytes representing a secp256k1 public key
     * @return The 20-byte EVM address or an empty byte array if the input is invalid
     */
    public static byte[] recoverAddressFromPubKey(byte @Nullable [] publicKeyBytes) {
        if (publicKeyBytes == null || publicKeyBytes.length != ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH) {
            return DomainUtils.EMPTY_BYTE_ARRAY;
        }

        try {
            final var point = EC_DOMAIN_PARAMETERS.getCurve().decodePoint(publicKeyBytes);

            if (!point.isValid()) {
                return DomainUtils.EMPTY_BYTE_ARRAY;
            }

            final var uncompressed = point.normalize().getEncoded(false);
            final var raw64 = Arrays.copyOfRange(uncompressed, 1, 65);

            final var digest = new KeccakDigest(256);
            digest.update(raw64, 0, raw64.length);

            final var hash = new byte[32];
            digest.doFinal(hash, 0);

            return Arrays.copyOfRange(hash, 12, 32);
        } catch (final Exception e) {
            return DomainUtils.EMPTY_BYTE_ARRAY;
        }
    }

    private static void checkSignatureComponentInBounds(byte[] curvePoint) {
        final var value = new BigInteger(1, curvePoint);
        if (value.compareTo(BigInteger.ONE) < 0) {
            throw new IllegalArgumentException("Curve point must be >= 1");
        }
        if (value.compareTo(CURVE_N) >= 0) {
            throw new IllegalArgumentException("Curve point must be < N");
        }
    }

    private static ECPoint decompressKey(BigInteger x, boolean yBit) {
        final var converter = new X9IntegerConverter();
        final var compressed =
                converter.integerToBytes(x, 1 + converter.getByteLength(EC_DOMAIN_PARAMETERS.getCurve()));
        compressed[0] = (byte) (yBit ? 0x03 : 0x02);
        return EC_DOMAIN_PARAMETERS.getCurve().decodePoint(compressed);
    }

    private static byte[] keccak256(byte[] message) {
        final var digest = new KeccakDigest(256);
        digest.update(message, 0, message.length);
        final var hash = new byte[32];
        digest.doFinal(hash, 0);
        return hash;
    }

    private static byte[] leftPadTo32Bytes(byte[] value) {
        if (value.length >= 32) {
            return Arrays.copyOfRange(value, value.length - 32, value.length);
        }

        final var padded = new byte[32];
        System.arraycopy(value, 0, padded, 32 - value.length, value.length);
        return padded;
    }

    @Nullable
    private static ECPoint recoverFromSignature(int recId, BigInteger r, BigInteger s, byte[] messageHash) {
        final var i = BigInteger.valueOf(recId / 2L);
        final var x = r.add(i.multiply(CURVE_N));
        final var prime = SecP256K1Curve.q;
        if (x.compareTo(prime) >= 0) {
            return null;
        }

        final var rPoint = decompressKey(x, (recId & 1) == 1);
        if (!rPoint.multiply(CURVE_N).isInfinity()) {
            return null;
        }

        final var e = new BigInteger(1, messageHash);
        final var eInv = BigInteger.ZERO.subtract(e).mod(CURVE_N);
        final var rInv = r.modInverse(CURVE_N);
        final var srInv = rInv.multiply(s).mod(CURVE_N);
        final var eInvrInv = rInv.multiply(eInv).mod(CURVE_N);
        return ECAlgorithms.sumOfTwoMultiplies(EC_DOMAIN_PARAMETERS.getG(), eInvrInv, rPoint, srInv);
    }
}
