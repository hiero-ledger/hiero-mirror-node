// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.common.collect.Range;
import java.io.IOException;
import org.hiero.mirror.common.util.RangeUtils;

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

        gen.writeString(RangeUtils.rangeToString(range));
    }
}
