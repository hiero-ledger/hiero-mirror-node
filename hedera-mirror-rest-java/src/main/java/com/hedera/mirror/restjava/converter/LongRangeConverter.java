// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.converter;

import com.google.common.collect.BoundType;
import com.google.common.collect.Range;
import jakarta.validation.constraints.NotNull;
import org.jooq.postgres.extensions.types.LongRange;
import org.springframework.core.convert.converter.Converter;

@SuppressWarnings("java:S6548")
public class LongRangeConverter implements Converter<LongRange, Range<Long>> {

    public static final LongRangeConverter INSTANCE = new LongRangeConverter();

    @Override
    public Range<Long> convert(@NotNull LongRange source) {
        var lower = source.lower();
        var lowerType = source.lowerIncluding() ? BoundType.CLOSED : BoundType.OPEN;
        var upper = source.upper();
        var upperType = source.upperIncluding() ? BoundType.CLOSED : BoundType.OPEN;

        if (lower == null && upper == null) {
            return Range.all();
        } else if (upper == null) {
            return Range.downTo(lower, lowerType);
        } else if (lower == null) {
            return Range.upTo(upper, upperType);
        } else {
            return Range.range(lower, lowerType, upper, upperType);
        }
    }
}
