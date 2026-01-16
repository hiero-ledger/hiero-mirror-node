// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import static org.hiero.mirror.common.converter.EntityIdSerializer.INSTANCE;

import com.fasterxml.jackson.core.JsonGenerator;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityIdSerializerTest {
    @Mock
    JsonGenerator jsonGenerator;

    @Test
    void testNull() throws Exception {
        // when
        INSTANCE.serialize(null, jsonGenerator, null);

        // then
        Mockito.verify(jsonGenerator).writeNull();
    }

    @Test
    void testEmpty() throws Exception {
        // when
        INSTANCE.serialize(EntityId.EMPTY, jsonGenerator, null);

        // then
        Mockito.verify(jsonGenerator).writeNull();
    }

    @Test
    void testEntity() throws Exception {
        // when
        var entity = EntityId.of(10L, 20L, 30L);
        INSTANCE.serialize(entity, jsonGenerator, null);

        // then
        Mockito.verify(jsonGenerator).writeNumber(entity.getId());
    }
}
