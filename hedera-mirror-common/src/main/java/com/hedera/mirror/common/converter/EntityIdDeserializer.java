// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.common.converter;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.hedera.mirror.common.domain.entity.EntityId;
import java.io.IOException;

@SuppressWarnings("java:S6548")
public class EntityIdDeserializer extends JsonDeserializer<EntityId> {

    public static final EntityIdDeserializer INSTANCE = new EntityIdDeserializer();

    @Override
    public EntityId deserialize(JsonParser jsonParser, DeserializationContext context) throws IOException {
        Long value = jsonParser.readValueAs(Long.class);
        return value != null ? EntityId.of(value) : null;
    }
}
