// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.common.converter;

import com.hedera.mirror.common.domain.entity.EntityId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;

@Converter
@ConfigurationPropertiesBinding
@SuppressWarnings("java:S6548")
public class EntityIdConverter implements AttributeConverter<EntityId, Long> {

    public static final EntityIdConverter INSTANCE = new EntityIdConverter();

    @Override
    public Long convertToDatabaseColumn(EntityId entityId) {
        if (EntityId.isEmpty(entityId)) {
            return null;
        }
        return entityId.getId();
    }

    @Override
    public EntityId convertToEntityAttribute(Long encodedId) {
        if (encodedId == null) {
            return null;
        }
        return EntityId.of(encodedId);
    }
}
