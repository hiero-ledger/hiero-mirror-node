// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.converter;

import jakarta.inject.Named;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.domain.Sort;

@Named
@ConfigurationPropertiesBinding
public class OrderConverter implements Converter<String, Sort.Direction> {
    @Override
    public Sort.Direction convert(String order) {
        return Sort.Direction.fromString(order);
    }
}
