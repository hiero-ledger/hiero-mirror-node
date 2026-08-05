// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import com.google.common.collect.Range;
import lombok.SneakyThrows;
import org.hiero.mirror.common.util.RangeUtils;
import org.postgresql.util.PGobject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.stereotype.Component;

@Component
@WritingConverter
public class RangeToPGobjectWritingConverter implements Converter<Range<Long>, PGobject> {

    @Override
    @SneakyThrows
    public PGobject convert(Range<Long> source) {
        if (source == null) {
            return null;
        }

        PGobject pgObject = new PGobject();
        pgObject.setType("int8range");
        pgObject.setValue(RangeUtils.rangeToString(source));
        return pgObject;
    }
}
