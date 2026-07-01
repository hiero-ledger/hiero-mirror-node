// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.util;

import java.util.Arrays;
import lombok.experimental.UtilityClass;
import org.bouncycastle.crypto.digests.KeccakDigest;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
@UtilityClass
public final class SignatureUtils {

    public static final int ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH = 33;
    private static final ECDomainParameters EC_DOMAIN_PARAMETERS;

    static {
        final var curveParams = CustomNamedCurves.getByName("secp256k1");
        EC_DOMAIN_PARAMETERS = new ECDomainParameters(
                curveParams.getCurve(), curveParams.getG(), curveParams.getN(), curveParams.getH());
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
}
