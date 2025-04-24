// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.converter;

import jakarta.inject.Named;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.util.StringUtils;

@Named
@ConfigurationPropertiesBinding
public class ByteArrayFromStringConverter implements Converter<String, byte[]> {
    private static final Pattern HEX_STRING_PATTERN = Pattern.compile("^(0x)?[0-9A-Fa-f]+$");

    @Override
    public byte[] convert(String source) {
        if (!StringUtils.hasLength(source)) {
            return null;
        }

        if (HEX_STRING_PATTERN.matcher(source).matches()) {
            if (source.length() % 2 != 0) {
                source = "0" + source;
            }
            return HexFormat.of().parseHex(source.replace("0x", ""));
        }

        return source.getBytes(StandardCharsets.UTF_8);
    }
}
