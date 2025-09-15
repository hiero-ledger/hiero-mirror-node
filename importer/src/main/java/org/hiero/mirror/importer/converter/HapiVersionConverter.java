// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.converter;

import jakarta.inject.Named;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.util.Version;

@Named
@ConfigurationPropertiesBinding
public class HapiVersionConverter implements Converter<String, Version> {

    @Override
    public Version convert(String source) {
        String truncatedSource = source.contains("-") ? source.substring(0, source.indexOf("-")) : source;
        return Version.parse(truncatedSource);
    }
}
