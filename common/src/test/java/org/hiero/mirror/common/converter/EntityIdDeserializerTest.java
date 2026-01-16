// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.converter.EntityIdDeserializer.INSTANCE;
import static org.mockito.Mockito.doReturn;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityIdDeserializerTest {

    @Mock
    private JsonParser jsonParser;

    @Test
    void deserialize() throws IOException {
        doReturn(98L).when(jsonParser).readValueAs(Long.class);
        var actual = INSTANCE.deserialize(jsonParser, context());
        assertThat(actual).isEqualTo(EntityId.of(98L));
    }

    @Test
    void deserializeNull() throws IOException {
        doReturn(null).when(jsonParser).readValueAs(Long.class);
        assertThat(INSTANCE.deserialize(jsonParser, context())).isNull();
    }

    private DeserializationContext context() {
        return new ObjectMapper().getDeserializationContext();
    }
}
