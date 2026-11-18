// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import com.google.common.collect.Range;
import org.hiero.mirror.common.util.RangeUtils;
import org.jspecify.annotations.Nullable;
import org.postgresql.util.PGobject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

@ReadingConverter
public class PGobjectToRangeReadingConverter implements Converter<PGobject, Range<Long>> {

    @Override
    public @Nullable Range<Long> convert(PGobject source) {
        final var value = source.getValue();
        if (value == null || value.isEmpty()) {
            return null;
        }

        // Delegate to the bracket-aware parser so the read path honors ]/( and matches RangeUtils.rangeToString.
        // The "empty" literal is read as an empty range, the same as Hibernate did.
        return RangeUtils.rangeLong(value);
    }
}
