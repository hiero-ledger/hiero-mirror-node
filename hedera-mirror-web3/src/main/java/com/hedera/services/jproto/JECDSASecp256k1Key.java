// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.jproto;

import com.swirlds.common.utility.CommonUtils;
import java.util.Arrays;

/**
 * Maps to proto Key of type ECDSA_secp256k1Key
 */
public class JECDSASecp256k1Key extends JKey {
    private final byte[] ecdsaSecp256k1Key;
    public static final int ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH = 33;

    public JECDSASecp256k1Key(final byte[] ecdsaSecp256k1Key) {
        this.ecdsaSecp256k1Key = ecdsaSecp256k1Key;
    }

    @Override
    public boolean isEmpty() {
        return ((null == ecdsaSecp256k1Key) || (0 == ecdsaSecp256k1Key.length));
    }

    @Override
    public boolean isValid() {
        return !(isEmpty()
                || (ecdsaSecp256k1Key.length != ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH)
                || (ecdsaSecp256k1Key[0] != 0x02 && ecdsaSecp256k1Key[0] != 0x03));
    }

    @Override
    public String toString() {
        return "<JECDSASecp256k1Key: ecdsaSecp256k1Key hex=" + CommonUtils.hex(ecdsaSecp256k1Key) + ">";
    }

    @Override
    public boolean hasECDSAsecp256k1Key() {
        return true;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (o == null || JECDSASecp256k1Key.class != o.getClass()) {
            return false;
        }
        final var that = (JECDSASecp256k1Key) o;
        return Arrays.equals(this.ecdsaSecp256k1Key, that.ecdsaSecp256k1Key);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(ecdsaSecp256k1Key);
    }

    @Override
    public byte[] getECDSASecp256k1Key() {
        return ecdsaSecp256k1Key;
    }
}
