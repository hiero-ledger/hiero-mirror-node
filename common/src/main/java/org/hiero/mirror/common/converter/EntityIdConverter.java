// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.stereotype.Component;

public class EntityIdConverter {

    @Component
    @WritingConverter
    public static class Writer implements Converter<EntityId, Long> {
        @Override
        public Long convert(EntityId entityId) {
            return EntityId.isEmpty(entityId) ? null : entityId.getId();
        }
    }

    @Component
    @ReadingConverter
    public static class Reader implements Converter<Long, EntityId> {
        @Override
        public EntityId convert(Long encodedId) {
            return encodedId == null ? null : EntityId.of(encodedId);
        }
    }
}
