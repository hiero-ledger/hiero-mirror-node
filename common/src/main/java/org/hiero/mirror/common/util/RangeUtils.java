// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.util;

import com.google.common.collect.Range;
import lombok.experimental.UtilityClass;

@UtilityClass
public class RangeUtils {

    public static String rangeToString(Range<Long> range) {
        if (range == null) return null;
        String lower = range.hasLowerBound() ? range.lowerEndpoint().toString() : "";
        String upper = range.hasUpperBound() ? range.upperEndpoint().toString() : "";
        return "[" + lower + "," + upper + ")";
    }

    public static Range<Long> rangeLong(String rangeStr) {
        if (rangeStr == null || rangeStr.isEmpty()) return null;
        var str = rangeStr.trim();
        boolean lowerInclusive = str.startsWith("[");
        boolean upperInclusive = str.endsWith("]");
        var inner = str.substring(1, str.length() - 1);
        var commaIdx = inner.indexOf(',');
        var lowerStr = inner.substring(0, commaIdx).trim();
        var upperStr = inner.substring(commaIdx + 1).trim();

        if (lowerStr.isEmpty() && upperStr.isEmpty()) {
            return Range.all();
        } else if (lowerStr.isEmpty()) {
            long upper = Long.parseLong(upperStr);
            return upperInclusive ? Range.atMost(upper) : Range.lessThan(upper);
        } else if (upperStr.isEmpty()) {
            long lower = Long.parseLong(lowerStr);
            return lowerInclusive ? Range.atLeast(lower) : Range.greaterThan(lower);
        } else {
            long lower = Long.parseLong(lowerStr);
            long upper = Long.parseLong(upperStr);
            if (lowerInclusive && !upperInclusive) {
                return Range.closedOpen(lower, upper);
            } else if (lowerInclusive) {
                return Range.closed(lower, upper);
            } else if (!upperInclusive) {
                return Range.open(lower, upper);
            } else {
                return Range.openClosed(lower, upper);
            }
        }
    }
}
