// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import static org.hiero.mirror.common.converter.ObjectToStringSerializer.INSTANCE;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ObjectToStringSerializerTest {

    @Mock
    private JsonGenerator jsonGenerator;

    @Test
    void serialize() throws IOException {
        INSTANCE.serialize(Entity.builder().id(1).type("unknown").build(), jsonGenerator, null);
        verify(jsonGenerator).writeString("{\"id\":1,\"type\":\"unknown\"}");
    }

    @Test
    void serializeList() throws IOException {
        var entities = List.of(
                Entity.builder().id(1).type("unknown").build(),
                Entity.builder().id(2).type(null).build());
        INSTANCE.serialize(entities, jsonGenerator, null);
        verify(jsonGenerator).writeString("[{\"id\":1,\"type\":\"unknown\"},{\"id\":2,\"type\":null}]");
    }

    @Test
    void serializeNull() throws IOException {
        INSTANCE.serialize(null, jsonGenerator, null);
        verify(jsonGenerator).writeString("null");
    }

    @AllArgsConstructor
    @Builder
    @Data
    private static class Entity {
        private long id;
        private String type;
    }
}
