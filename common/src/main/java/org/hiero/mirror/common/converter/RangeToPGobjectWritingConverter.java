// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import com.google.common.collect.Range;
import lombok.SneakyThrows;
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
        // Logic to convert Range to "[lower,upper)" format
        String lower = source.hasLowerBound() ? source.lowerEndpoint().toString() : "";
        String upper = source.hasUpperBound() ? source.upperEndpoint().toString() : "";
        pgObject.setValue(String.format("[%s,%s)", lower, upper));
        return pgObject;
    }
}
