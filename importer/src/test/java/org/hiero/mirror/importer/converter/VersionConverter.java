// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.converter;

import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.params.converter.ArgumentConversionException;
import org.junit.jupiter.params.converter.ArgumentConverter;
import org.springframework.data.util.Version;

public class VersionConverter implements ArgumentConverter {

    @Override
    public Object convert(Object input, ParameterContext context) throws ArgumentConversionException {
        if (input == null) {
            return null;
        }

        if (input instanceof String inputString) {
            String truncatedString =
                    inputString.contains("-") ? inputString.substring(0, inputString.indexOf("-")) : inputString;
            return Version.parse(truncatedString);
        } else {
            throw new ArgumentConversionException("Input " + input + " is not a string");
        }
    }
}
