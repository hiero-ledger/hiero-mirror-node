// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.converter;

import jakarta.inject.Named;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;

@Named
@ConfigurationPropertiesBinding
public class EntityIdConverter implements Converter<String, EntityId> {
    @Override
    public EntityId convert(String source) {
        return EntityId.of(source);
    }
}
