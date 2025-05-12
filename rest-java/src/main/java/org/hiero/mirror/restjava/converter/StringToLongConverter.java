// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.converter;

import com.hedera.mirror.common.domain.entity.EntityId;
import jakarta.inject.Named;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.util.StringUtils;

@Named
@ConfigurationPropertiesBinding
public class StringToLongConverter implements Converter<String, Long> {
    @Override
    public Long convert(String source) {
        if (!StringUtils.hasText(source)) {
            return null;
        }

        var parts = source.split("\\.");
        if (parts.length == 3) {
            return EntityId.of(source).getId();
        }
        return Long.parseLong(source);
    }
}
