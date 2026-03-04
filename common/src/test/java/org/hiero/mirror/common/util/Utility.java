// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.util;

import com.hederahashgraph.api.proto.java.Timestamp;
import java.time.Instant;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Utility {

    /**
     * @return Timestamp from an instant
     */
    public static Timestamp instantToTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    public static Instant convertToInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}
