// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.util;

import com.google.common.collect.BoundType;
import com.google.common.collect.Range;
import lombok.experimental.UtilityClass;

@UtilityClass
public class RangeUtils {

    public static String rangeToString(Range<Long> range) {
        if (range == null) return null;
        final var lowerBracket = range.hasLowerBound() && range.lowerBoundType() == BoundType.CLOSED ? "[" : "(";
        final var lower = range.hasLowerBound() ? range.lowerEndpoint().toString() : "";
        final var upper = range.hasUpperBound() ? range.upperEndpoint().toString() : "";
        final var upperBracket = range.hasUpperBound() && range.upperBoundType() == BoundType.CLOSED ? "]" : ")";
        return lowerBracket + lower + "," + upper + upperBracket;
    }

    public static Range<Long> rangeLong(String rangeStr) {
        if (rangeStr == null || rangeStr.isEmpty()) return null;
        var str = rangeStr.trim();
        final var lowerBoundType = str.startsWith("[") ? BoundType.CLOSED : BoundType.OPEN;
        final var upperBoundType = str.endsWith("]") ? BoundType.CLOSED : BoundType.OPEN;
        var inner = str.substring(1, str.length() - 1);
        var commaIdx = inner.indexOf(',');
        var lowerStr = inner.substring(0, commaIdx).trim();
        var upperStr = inner.substring(commaIdx + 1).trim();

        if (lowerStr.isEmpty() && upperStr.isEmpty()) {
            return Range.all();
        } else if (lowerStr.isEmpty()) {
            return Range.upTo(Long.parseLong(upperStr), upperBoundType);
        } else if (upperStr.isEmpty()) {
            return Range.downTo(Long.parseLong(lowerStr), lowerBoundType);
        } else {
            return Range.range(Long.parseLong(lowerStr), lowerBoundType, Long.parseLong(upperStr), upperBoundType);
        }
    }
}
