// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.common.util;

import java.security.SecureRandom;
import java.time.Instant;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CommonUtils {

    private static final SecureRandom RANDOM = new SecureRandom();

    public static byte[] nextBytes(int length) {
        var bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    public static Instant instant(long nanos) {
        final long seconds = nanos / 1_000_000_000;
        final int remainingNanos = (int) (nanos % 1_000_000_000);
        return Instant.ofEpochSecond(seconds, remainingNanos);
    }
}
