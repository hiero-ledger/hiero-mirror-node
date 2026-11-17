// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.util;

import com.google.common.collect.BoundType;
import com.google.common.collect.Range;
import lombok.experimental.UtilityClass;

/**
 * Converts between Guava {@link Range}s and the PostgreSQL range text format, e.g. {@code [1,5)}, {@code (,)} or
 * {@code empty}.
 */
@UtilityClass
public class RangeUtils {

    private static final String EMPTY = "empty";
    // Same empty range as Hypersistence's PostgreSQLGuavaRangeType, so both parsers produce equal values
    private static final Range<Long> EMPTY_LONG_RANGE = Range.closedOpen(Long.MIN_VALUE, Long.MIN_VALUE);

    public static Range<Long> rangeLong(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        if (EMPTY.equals(text)) {
            return EMPTY_LONG_RANGE;
        }

        final int comma = text.indexOf(',');
        if (comma == -1) {
            throw new IllegalArgumentException("Invalid range: " + text);
        }

        final var lowerType = text.charAt(0) == '[' ? BoundType.CLOSED : BoundType.OPEN;
        final var upperType = text.charAt(text.length() - 1) == ']' ? BoundType.CLOSED : BoundType.OPEN;
        final var lower = text.substring(1, comma);
        final var upper = text.substring(comma + 1, text.length() - 1);

        if (lower.isEmpty() && upper.isEmpty()) {
            return Range.all();
        }

        if (lower.isEmpty()) {
            return Range.upTo(Long.parseLong(upper), upperType);
        }

        if (upper.isEmpty()) {
            return Range.downTo(Long.parseLong(lower), lowerType);
        }

        return Range.range(Long.parseLong(lower), lowerType, Long.parseLong(upper), upperType);
    }

    public static String rangeToString(Range<?> range) {
        if (range == null) {
            return null;
        }

        if (range.isEmpty()) {
            return EMPTY;
        }

        final var lowerBracket = range.hasLowerBound() && range.lowerBoundType() == BoundType.CLOSED ? "[" : "(";
        final var lower = range.hasLowerBound() ? range.lowerEndpoint().toString() : "";
        final var upper = range.hasUpperBound() ? range.upperEndpoint().toString() : "";
        final var upperBracket = range.hasUpperBound() && range.upperBoundType() == BoundType.CLOSED ? "]" : ")";
        return lowerBracket + lower + "," + upper + upperBracket;
    }
}
