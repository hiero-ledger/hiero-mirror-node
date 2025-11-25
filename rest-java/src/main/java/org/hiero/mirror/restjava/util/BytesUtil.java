// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.util;

import org.apache.tuweni.bytes.MutableBytes32;

public final class BytesUtil {

    private BytesUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static byte[] incrementByteArray(byte[] bytes) {
        return MutableBytes32.wrap(bytes).increment().toArrayUnsafe();
    }

    public static byte[] decrementByteArray(byte[] bytes) {
        return MutableBytes32.wrap(bytes).decrement().toArrayUnsafe();
    }
}
