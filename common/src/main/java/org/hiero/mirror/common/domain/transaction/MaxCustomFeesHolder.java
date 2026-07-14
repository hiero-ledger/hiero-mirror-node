// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Single-valued JDBC column type for {@code transaction.max_custom_fees} (bytea[]). A bare {@code byte[][]} is treated
 * by Spring Data JDBC as an array of its component type, which binds parameters (including nulls) as bytea instead of
 * bytea[].
 */
public record MaxCustomFeesHolder(byte[][] items) implements Serializable {

    public static MaxCustomFeesHolder of(byte[][] items) {
        if (items == null) {
            return null;
        }
        return new MaxCustomFeesHolder(items);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MaxCustomFeesHolder holder && Arrays.deepEquals(items, holder.items);
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(items);
    }

    @Override
    public String toString() {
        return Arrays.deepToString(items);
    }
}
