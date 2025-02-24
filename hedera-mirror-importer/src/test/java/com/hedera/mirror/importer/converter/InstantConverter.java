// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.converter;

import java.time.Instant;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.params.converter.ArgumentConversionException;
import org.junit.jupiter.params.converter.ArgumentConverter;

public final class InstantConverter implements ArgumentConverter {
    @Override
    public Object convert(Object input, ParameterContext parameterContext) throws ArgumentConversionException {
        if (null == input) {
            return null;
        }
        if (!(input instanceof String)) {
            throw new ArgumentConversionException(input + " is not a string");
        }
        return Instant.parse((String) input);
    }
}
