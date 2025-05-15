// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.converter;

import static org.hiero.mirror.importer.converter.ByteArrayArrayToHexSerializer.INSTANCE;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.core.JsonGenerator;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ByteArrayArrayToHexSerializerTest {

    @Mock
    private JsonGenerator jsonGenerator;

    @SneakyThrows
    @Test
    void testEmptyArray() {
        INSTANCE.serialize(new byte[][] {}, jsonGenerator, null);
        verify(jsonGenerator).writeString("{}");
    }

    @SneakyThrows
    @Test
    void testMultipleElementArray() {
        INSTANCE.serialize(new byte[][] {{0xa}, {0x1, 0xd}, null, {}}, jsonGenerator, null);
        verify(jsonGenerator).writeString("{\"\\\\x0a\",\"\\\\x010d\",null,\"\\\\x\"}");
    }

    @SneakyThrows
    @Test
    void testNull() {
        INSTANCE.serialize(null, jsonGenerator, null);
        verifyNoInteractions(jsonGenerator);
    }

    @SneakyThrows
    @Test
    void testSingleElementArray() {
        INSTANCE.serialize(new byte[][] {{0xa}}, jsonGenerator, null);
        verify(jsonGenerator).writeString("{\"\\\\x0a\"}");
    }
}
