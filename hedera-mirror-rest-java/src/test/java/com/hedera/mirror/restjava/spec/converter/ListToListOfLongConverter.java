// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec.converter;

import jakarta.inject.Named;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;

@Named
@ConfigurationPropertiesBinding
public class ListToListOfLongConverter implements Converter<List<?>, List<Long>> {
    @Override
    public List<Long> convert(List<?> source) {
        return source.stream()
                .map(value -> switch (value) {
                    case Integer intValue -> (long) intValue;
                    default -> throw new IllegalStateException("Invalid type: " + value.getClass());
                })
                .toList();
    }
}
