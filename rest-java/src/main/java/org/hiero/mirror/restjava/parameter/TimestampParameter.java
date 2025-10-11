// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.parameter;

import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.restjava.common.RangeOperator;
import org.hiero.mirror.restjava.common.RangeParameter;

public record TimestampParameter(RangeOperator operator, Long value) implements RangeParameter<Long> {

    public static final String ERROR = "Invalid timestamp parameter";
    public static final TimestampParameter EMPTY = new TimestampParameter(null, null);

    private static final Pattern PATTERN =
            Pattern.compile("^((eq|gt|gte|lt|lte|ne):)?(\\d{1,17})(\\.(\\d{1,9}))?$", Pattern.CASE_INSENSITIVE);

    public static TimestampParameter valueOf(String param) {
        if (StringUtils.isBlank(param)) {
            return EMPTY;
        }

        var matcher = PATTERN.matcher(param);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(ERROR);
        }

        final var operator = parseOperator(matcher.group(2));
        final var timestamp = parseTimestamp(matcher.group(3), matcher.group(5));
        return new TimestampParameter(operator, timestamp);
    }

    private static RangeOperator parseOperator(String name) {
        if (StringUtils.isEmpty(name)) {
            return RangeOperator.EQ;
        }

        final var operator = RangeOperator.of(name);

        if (operator == RangeOperator.NE) {
            throw new IllegalArgumentException(ERROR);
        }

        return operator;
    }

    private static long parseTimestamp(String secondsStr, String nanosStr) {
        try {
            final long seconds = Long.parseLong(secondsStr);
            final long nanos = StringUtils.isNotEmpty(nanosStr) ? Long.parseLong(nanosStr) : 0L;
            return DomainUtils.convertToNanos(seconds, nanos);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(ERROR);
        }
    }
}
