// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.converter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;

@SuppressWarnings("java:S6548")
public class LongArrayArraySerializer extends JsonSerializer<long[][]> {

    public static final LongArrayArraySerializer INSTANCE = new LongArrayArraySerializer();

    private static final String DELIMITER = ",";
    private static final String END = "}";
    private static final String START = "{";

    private LongArrayArraySerializer() {}

    @Override
    public void serialize(long[][] value, JsonGenerator jsonGenerator, SerializerProvider serializers)
            throws IOException {
        if (value == null) {
            return;
        }

        var rows = Arrays.stream(value)
                .map(longArray ->
                        Arrays.stream(longArray).mapToObj(Long::toString).collect(Collectors.joining(DELIMITER)))
                .map(str -> START + str + END)
                .collect(Collectors.joining(DELIMITER));

        jsonGenerator.writeString(START + rows + END);
    }
}
