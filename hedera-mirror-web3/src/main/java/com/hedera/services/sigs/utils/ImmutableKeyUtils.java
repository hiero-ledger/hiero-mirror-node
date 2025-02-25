// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.sigs.utils;

import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;

/**
 * Exact copy from hedera-services
 */
public final class ImmutableKeyUtils {
    private ImmutableKeyUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static final Key IMMUTABILITY_SENTINEL_KEY =
            Key.newBuilder().setKeyList(KeyList.getDefaultInstance()).build();

    public static boolean signalsKeyRemoval(final Key source) {
        return IMMUTABILITY_SENTINEL_KEY.equals(source);
    }
}
