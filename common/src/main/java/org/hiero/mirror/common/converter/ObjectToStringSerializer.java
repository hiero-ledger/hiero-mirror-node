// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.hypersistence.utils.hibernate.type.util.JsonConfiguration;
import java.io.IOException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("java:S6548")
public class ObjectToStringSerializer extends JsonSerializer<Object> {

    public static final ObjectToStringSerializer INSTANCE = new ObjectToStringSerializer();
    public static final ObjectMapper OBJECT_MAPPER;

    static {
        var module = new SimpleModule();
        module.addDeserializer(EntityId.class, EntityIdDeserializer.INSTANCE);
        module.addSerializer(EntityId.class, EntityIdSerializer.INSTANCE);

        OBJECT_MAPPER = new ObjectMapper()
                .registerModule(module)
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        // Configure hyperpersistence utils so that JsonBinaryType uses the same object mapper
        JsonConfiguration.INSTANCE.getObjectMapperWrapper().setObjectMapper(OBJECT_MAPPER);
    }

    public static void init() {
        // Called by other classes to ensure the static initializer runs
    }

    @Override
    public void serialize(Object o, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        var json = OBJECT_MAPPER.writeValueAsString(o);
        gen.writeString(json);
    }
}
