// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec.converter;

import jakarta.inject.Named;
import java.util.List;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;

@Named
@ConfigurationPropertiesBinding
public class ByteArrayFromListConverter implements Converter<List<?>, byte[]> {
    @Override
    public byte[] convert(List<?> source) {
        if (source.isEmpty()) {
            return new byte[0];
        }

        return ArrayUtils.toPrimitive(source.stream()
                .map(value -> switch (value) {
                    case String strValue -> Byte.parseByte(strValue.replace("0x", ""), 16);
                    case Integer intValue -> intValue.byteValue();
                    case Byte byteValue -> byteValue;
                    default -> throw new IllegalArgumentException("Unsupported type: " + value.getClass());
                })
                .toArray(Byte[]::new));
    }
}
