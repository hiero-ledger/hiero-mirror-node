// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.converter;

import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.params.converter.ArgumentConversionException;
import org.junit.jupiter.params.converter.TypedArgumentConverter;

public class HexToByteArrayConverter extends TypedArgumentConverter<String, byte[]> {

    HexToByteArrayConverter() {
        super(String.class, byte[].class);
    }

    @Override
    protected byte[] convert(String source) throws ArgumentConversionException {
        if (source == null) {
            return null;
        }

        return Hex.decode(source);
    }
}
