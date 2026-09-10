// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import com.google.common.collect.Range;
import org.hiero.mirror.common.util.RangeUtils;
import org.postgresql.util.PGobject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

@ReadingConverter
public class PGobjectToRangeReadingConverter implements Converter<PGobject, Range<Long>> {

    @Override
    public Range<Long> convert(PGobject source) {
        String value = source.getValue();
        // RangeUtils.rangeLong cannot parse the empty-range literal
        if (value == null || value.isEmpty() || value.equalsIgnoreCase("empty")) {
            return null;
        }

        // Delegate to the bracket-aware parser so the read path honors ]/( and matches RangeUtils.rangeToString.
        return RangeUtils.rangeLong(value);
    }
}
