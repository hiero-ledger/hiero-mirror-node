// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.converter;

import jakarta.inject.Named;
import org.hiero.mirror.restjava.common.SupplyQuery;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;

@Named
@ConfigurationPropertiesBinding
public class SupplyQueryConverter implements Converter<String, SupplyQuery> {

    @Override
    public SupplyQuery convert(String source) {
        return SupplyQuery.valueOf(source.toUpperCase());
    }
}
