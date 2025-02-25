// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.converter;

import com.hedera.mirror.common.domain.entity.EntityId;
import jakarta.inject.Named;
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
