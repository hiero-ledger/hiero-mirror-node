// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.converter;

import com.google.common.collect.Range;
import io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import jakarta.inject.Named;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.util.StringUtils;

@Named
@ConfigurationPropertiesBinding
public class RangeFromStringConverter implements Converter<String, Range<Long>> {
    private static final String RANGE_REGEX = "^([\\[(])?(\\d*)?,\\s*(\\d*)?([])])$";
    private static final Pattern RANGE_PATTERN = Pattern.compile(RANGE_REGEX);

    @Override
    public Range<Long> convert(String source) {
        if (!StringUtils.hasText(source)) {
            return null;
        }

        var cleanedSource = source.replaceAll("\\s", "");

        if (!RANGE_PATTERN.matcher(cleanedSource).matches()) {
            throw new IllegalArgumentException("Range string is not valid, '%s'".formatted(source));
        }

        return PostgreSQLGuavaRangeType.ofString(cleanedSource, Long::parseLong, Long.class);
    }
}
