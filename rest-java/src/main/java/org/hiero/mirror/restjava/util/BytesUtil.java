// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.util;

public final class BytesUtil {

    private BytesUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static byte[] incrementByteArray(byte[] bytes) {
        byte[] result = bytes.clone();

        for (int i = result.length - 1; i >= 0; i--) {
            int v = (result[i] & 0xFF) + 1;
            result[i] = (byte) v;

            if (v <= 0xFF) {
                break;
            }

            result[i] = 0;
        }

        return result;
    }

    public static byte[] decrementByteArray(byte[] bytes) {
        byte[] result = bytes.clone();

        for (int i = result.length - 1; i >= 0; i--) {
            int v = (result[i] & 0xFF) - 1;
            result[i] = (byte) v;

            if (v >= 0) {
                break;
            }

            result[i] = (byte) 0xFF;
        }

        return result;
    }
}
