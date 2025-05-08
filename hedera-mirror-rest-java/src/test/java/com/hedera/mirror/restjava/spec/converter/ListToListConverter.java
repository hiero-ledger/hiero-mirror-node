// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec.converter;

import com.hedera.mirror.common.domain.entity.EntityId;
import jakarta.inject.Named;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;

@Named
@ConfigurationPropertiesBinding
public class ListToListConverter implements Converter<List<?>, List<?>> {
    @Override
    public List<?> convert(List<?> source) {
        return source.stream()
                .map(value -> switch (value) {
                    case Integer intValue -> (long) intValue;
                    case String stringValue -> {
                        if (EntityId.isValid(stringValue)) {
                            yield EntityId.of(stringValue).getId();
                        }
                        yield value;
                    }
                    default -> value;
                })
                .toList();
    }
}
