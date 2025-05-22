// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.converter;

import jakarta.inject.Named;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.util.StringUtils;

@Named
@ConfigurationPropertiesBinding
public class EntityIdFromStringConverter implements Converter<String, EntityId> {
    @Override
    public EntityId convert(String source) {
        if (!StringUtils.hasText(source)) {
            return null;
        }

        var parts = source.split("\\.");
        if (parts.length == 3) {
            return EntityId.of(source);
        }

        return EntityId.of(Long.parseLong(source));
    }
}
