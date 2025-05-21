// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;

public class PubSubEntityIdSerializer extends JsonSerializer<EntityId> {

    private static final String SHARD_NUM = "shardNum";
    private static final String REALM_NUM = "realmNum";
    private static final String ENTITY_NUM = "entityNum";
    private static final String TYPE = "type";

    @Override
    public void serialize(EntityId entityId, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        gen.writeFieldName(SHARD_NUM);
        gen.writeRawValue(String.valueOf(entityId.getShard()));
        gen.writeFieldName(REALM_NUM);
        gen.writeRawValue(String.valueOf(entityId.getRealm()));
        gen.writeFieldName(ENTITY_NUM);
        gen.writeRawValue(String.valueOf(entityId.getNum()));
        gen.writeFieldName(TYPE);
        gen.writeNumber(EntityType.UNKNOWN.ordinal());
        gen.writeEndObject();
    }
}
