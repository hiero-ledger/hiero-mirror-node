// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("unchecked")
public class ListToStringSerializer extends JsonSerializer<List<?>> {

    public static final JsonSerializer<List<?>> INSTANCE = new ListToStringSerializer();
    private static final Class<?> HANDLED_TYPE = ListToStringSerializer.class;
    private static final String PREFIX = "{";
    private static final String SEPARATOR = ",";
    private static final String SUFFIX = "}";

    @Override
    public Class<List<?>> handledType() {
        return (Class<List<?>>) HANDLED_TYPE;
    }

    @Override
    public void serialize(List<?> list, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (list != null) {
            gen.writeString(PREFIX + StringUtils.join(list, SEPARATOR) + SUFFIX);
        }
    }
}
