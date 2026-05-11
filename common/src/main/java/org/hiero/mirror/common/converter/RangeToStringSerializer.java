// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.common.collect.Range;
import java.io.IOException;

public class RangeToStringSerializer extends StdSerializer<Range<Long>> {

    private static final long serialVersionUID = -2404098939768685161L;
    public static final RangeToStringSerializer INSTANCE = new RangeToStringSerializer();

    public RangeToStringSerializer() {
        super(Range.class, false);
    }

    @Override
    public void serialize(Range<Long> range, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (range == null || range.isEmpty()) {
            gen.writeNull();
            return;
        }

        String lower = range.hasLowerBound() ? range.lowerEndpoint().toString() : "";
        String upper = range.hasUpperBound() ? range.upperEndpoint().toString() : "";

        gen.writeString(String.format("[%s,%s)", lower, upper));
    }
}
