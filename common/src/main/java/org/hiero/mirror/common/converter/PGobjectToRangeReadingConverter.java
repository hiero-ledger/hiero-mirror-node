// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import com.google.common.collect.Range;
import org.postgresql.util.PGobject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

@ReadingConverter
public class PGobjectToRangeReadingConverter implements Converter<PGobject, Range<Long>> {

    @Override
    public Range<Long> convert(PGobject source) {
        String value = source.getValue();
        if (value == null || value.isEmpty() || value.equalsIgnoreCase("empty")) {
            return null;
        }

        // Postgres int8range format: [lower,upper)
        // We remove the brackets/parentheses and split by comma
        String[] parts = value.substring(1, value.length() - 1).split(",");

        Long lower = parts[0].isEmpty() ? null : Long.valueOf(parts[0]);
        Long upper = (parts.length < 2 || parts[1].isEmpty()) ? null : Long.valueOf(parts[1]);

        if (lower != null && upper != null) {
            return Range.closedOpen(lower, upper);
        } else if (lower != null) {
            return Range.atLeast(lower);
        } else if (upper != null) {
            return Range.lessThan(upper);
        }

        return Range.all();
    }
}
