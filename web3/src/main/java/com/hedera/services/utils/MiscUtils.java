// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.utils;

public final class MiscUtils {
    private MiscUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static long perm64(long x) {
        // Shifts: {30, 27, 16, 20, 5, 18, 10, 24, 30}
        x += x << 30;
        x ^= x >>> 27;
        x += x << 16;
        x ^= x >>> 20;
        x += x << 5;
        x ^= x >>> 18;
        x += x << 10;
        x ^= x >>> 24;
        x += x << 30;
        return x;
    }
}
