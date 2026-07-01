// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.util;

import java.util.Arrays;
import java.util.Collections;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.params.converter.ArgumentConversionException;
import org.junit.jupiter.params.converter.SimpleArgumentConverter;

public final class LongListConverter extends SimpleArgumentConverter {

    @Override
    protected Object convert(Object source, Class<?> targetType) throws ArgumentConversionException {
        if (source == null) {
            return Collections.emptyList();
        }

        if (source instanceof String input) {
            return Arrays.stream(StringUtils.split(input, ','))
                    .map(String::trim)
                    .map(Long::valueOf)
                    .toList();
        }

        return Collections.emptyList();
    }
}
