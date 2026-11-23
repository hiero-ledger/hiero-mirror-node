// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.google.common.collect.Range;
import java.io.IOException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hiero.mirror.common.util.RangeUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings({"java:S6548", "unchecked"}) // Singletons are fine
public class RangeToStringSerializer extends JsonSerializer<Range<? extends Number>> {

    public static final RangeToStringSerializer INSTANCE = new RangeToStringSerializer();
    private static final Class<?> HANDLED_TYPE = Range.class;

    @Override
    public void serialize(Range<? extends Number> range, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        if (range != null) {
            gen.writeString(RangeUtils.rangeToString(range));
        }
    }

    @Override
    public Class<Range<? extends Number>> handledType() {
        return (Class<Range<? extends Number>>) HANDLED_TYPE;
    }
}
