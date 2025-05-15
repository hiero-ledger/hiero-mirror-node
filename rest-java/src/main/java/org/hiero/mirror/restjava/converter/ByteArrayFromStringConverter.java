// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.converter;

import jakarta.inject.Named;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.util.StringUtils;

@Named
@ConfigurationPropertiesBinding
public class ByteArrayFromStringConverter implements Converter<String, byte[]> {
    @Override
    public byte[] convert(String source) {
        return StringUtils.hasLength(source) ? source.getBytes(StandardCharsets.UTF_8) : null;
    }
}
