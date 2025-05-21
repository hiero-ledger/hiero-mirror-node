// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.google.common.collect.Range;
import io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import java.io.IOException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings({"java:S6548", "unchecked"}) // Singletons are fine
public class RangeToStringSerializer extends JsonSerializer<Range<?>> {

    public static final RangeToStringSerializer INSTANCE = new RangeToStringSerializer();
    private static final Class<?> HANDLED_TYPE = Range.class;

    @Override
    public void serialize(Range<?> range, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (range != null) {
            gen.writeString(PostgreSQLGuavaRangeType.INSTANCE.asString(range));
        }
    }

    @Override
    public Class<Range<?>> handledType() {
        return (Class<Range<?>>) HANDLED_TYPE;
    }
}
