// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.converter;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.codec.binary.Base64;
import org.hiero.mirror.importer.TestUtils;
import org.junit.jupiter.api.Test;

final class Base64ToByteArrayConverterTest {

    private final Base64ToByteArrayConverter converter = new Base64ToByteArrayConverter();

    @Test
    void convert() {
        final byte[] expected = TestUtils.generateRandomByteArray(16);
        final var source = Base64.encodeBase64String(expected);
        assertThat(converter.convert(source)).isEqualTo(expected);
        assertThat(converter.convert("")).isEqualTo(new byte[0]);
    }
}
