// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.converter;

import jakarta.inject.Named;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;

@Named
@ConfigurationPropertiesBinding
public class EntityIdFromLongConverter implements Converter<Long, EntityId> {
    @Override
    public EntityId convert(Long entityId) {
        return entityId != null ? EntityId.of(entityId) : null;
    }
}
