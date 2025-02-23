/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
