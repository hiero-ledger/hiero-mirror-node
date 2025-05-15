// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.converter;

import com.hedera.mirror.common.domain.entity.EntityId;
import jakarta.inject.Named;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;

@Named
@ConfigurationPropertiesBinding
public class EntityIdFromIntegerConverter implements Converter<Integer, EntityId> {
    @Override
    public EntityId convert(Integer entityId) {
        return entityId != null ? EntityId.of(entityId) : null;
    }
}
