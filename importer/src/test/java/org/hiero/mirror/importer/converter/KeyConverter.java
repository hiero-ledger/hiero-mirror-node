// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.converter;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Key;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.params.converter.ArgumentConversionException;
import org.junit.jupiter.params.converter.ArgumentConverter;

public final class KeyConverter implements ArgumentConverter {

    @Override
    public Object convert(Object input, ParameterContext parameterContext) throws ArgumentConversionException {
        if (null == input) {
            return null;
        }
        if (!(input instanceof String inputString)) {
            throw new ArgumentConversionException(input + " is not a string");
        }

        if (inputString.isEmpty()) {
            return Key.newBuilder().build();
        } else {
            return Key.newBuilder()
                    .setEd25519(ByteString.copyFrom(inputString.getBytes()))
                    .build();
        }
    }
}
