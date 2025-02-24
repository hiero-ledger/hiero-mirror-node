// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;

public class ProtoJsonSerializer extends JsonSerializer<Message> {

    private static final JsonFormat.Printer PRINTER =
            JsonFormat.printer().includingDefaultValueFields().omittingInsignificantWhitespace();

    @Override
    public void serialize(Message message, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeRawValue(PRINTER.print(message));
    }
}
