// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.converter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import org.apache.commons.codec.binary.Hex;

@SuppressWarnings("java:S6548")
public class ByteArrayToHexSerializer extends JsonSerializer<byte[]> {

    public static final ByteArrayToHexSerializer INSTANCE = new ByteArrayToHexSerializer();
    static final String PREFIX = "\\x";

    private ByteArrayToHexSerializer() {}

    @Override
    public void serialize(byte[] value, JsonGenerator jsonGenerator, SerializerProvider serializers)
            throws IOException {
        if (value != null) {
            jsonGenerator.writeString(PREFIX + Hex.encodeHexString(value));
        }
    }
}
