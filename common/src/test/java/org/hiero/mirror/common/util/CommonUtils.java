// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.util;

import java.security.SecureRandom;
import java.time.Instant;
import lombok.experimental.UtilityClass;
import org.hiero.mirror.common.CommonProperties;

@UtilityClass
public class CommonUtils {

    private static final SecureRandom RANDOM = new SecureRandom();

    public static void copyCommonProperties(CommonProperties source, CommonProperties target) {
        target.setRealm(source.getRealm());
        target.setShard(source.getShard());
    }

    public static Instant instant(long nanos) {
        final long seconds = nanos / 1_000_000_000;
        final int remainingNanos = (int) (nanos % 1_000_000_000);
        return Instant.ofEpochSecond(seconds, remainingNanos);
    }

    public static byte[] nextBytes(int length) {
        var bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }
}
