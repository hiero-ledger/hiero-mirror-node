// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.common.converter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.hedera.mirror.common.domain.entity.EntityId;
import java.io.IOException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("java:S6548")
public class EntityIdSerializer extends JsonSerializer<EntityId> {

    public static final EntityIdSerializer INSTANCE = new EntityIdSerializer();

    @Override
    public Class<EntityId> handledType() {
        return EntityId.class;
    }

    @Override
    public void serialize(EntityId value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (!EntityId.isEmpty(value)) {
            gen.writeNumber(value.getId());
        } else {
            gen.writeNull();
        }
    }
}
