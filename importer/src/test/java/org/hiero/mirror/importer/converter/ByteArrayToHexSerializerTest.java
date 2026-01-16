// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.converter;

import static org.hiero.mirror.importer.converter.ByteArrayToHexSerializer.PREFIX;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.core.JsonGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ByteArrayToHexSerializerTest {

    private final ByteArrayToHexSerializer byteArrayToHexSerializer = ByteArrayToHexSerializer.INSTANCE;

    @Mock
    private JsonGenerator jsonGenerator;

    @Test
    void testNullBytes() throws Exception {
        byteArrayToHexSerializer.serialize(null, jsonGenerator, null);
        verifyNoInteractions(jsonGenerator);
    }

    @Test
    void testEmptyBytes() throws Exception {
        byteArrayToHexSerializer.serialize(new byte[0], jsonGenerator, null);
        verify(jsonGenerator).writeString(PREFIX);
    }

    @Test
    void testBytes() throws Exception {
        byteArrayToHexSerializer.serialize(new byte[] {0b0, 0b1, 0b10, 0b01111111}, jsonGenerator, null);
        verify(jsonGenerator).writeString(PREFIX + "0001027f");
    }
}
