// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.common.util;

import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.SystemEntity;
import java.security.SecureRandom;
import java.time.Instant;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CommonUtils {

    public static final EntityId DEFAULT_TREASURY_ACCOUNT =
            SystemEntity.TREASURY_ACCOUNT.getScopedEntityId(new CommonProperties());

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
