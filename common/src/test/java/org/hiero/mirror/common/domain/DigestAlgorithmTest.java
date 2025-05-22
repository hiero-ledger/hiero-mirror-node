// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DigestAlgorithmTest {

    @ParameterizedTest
    @EnumSource(value = DigestAlgorithm.class)
    void testEmptyHash(DigestAlgorithm digestAlgorithm) {
        String expected = Hex.encodeHexString(new byte[digestAlgorithm.getSize()]);
        assertThat(digestAlgorithm.getEmptyHash()).isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(value = DigestAlgorithm.class)
    void testIsHashEmpty(DigestAlgorithm digestAlgorithm) {
        assertThat(digestAlgorithm.isHashEmpty(null)).isTrue();
        assertThat(digestAlgorithm.isHashEmpty("")).isTrue();

        String emptyHash = Hex.encodeHexString(new byte[digestAlgorithm.getSize()]);
        assertThat(digestAlgorithm.isHashEmpty(emptyHash)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = DigestAlgorithm.class)
    void testIsHashEmptyWithNonEmptyHash(DigestAlgorithm digestAlgorithm) {
        assertThat(digestAlgorithm.isHashEmpty(" ")).isFalse();

        byte[] data = new byte[digestAlgorithm.getSize()];
        data[0] = 1;
        String nonEmptyHash = Hex.encodeHexString(data);
        assertThat(digestAlgorithm.isHashEmpty(nonEmptyHash)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = DigestAlgorithm.class)
    void testIsHashEmptyWithShortHash(DigestAlgorithm digestAlgorithm) {
        String shortHash = Hex.encodeHexString(new byte[digestAlgorithm.getSize() - 1]);
        assertThat(digestAlgorithm.isHashEmpty(shortHash)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = DigestAlgorithm.class)
    void testIsHashEmptyWithLongHash(DigestAlgorithm digestAlgorithm) {
        String longHash = Hex.encodeHexString(new byte[digestAlgorithm.getSize() + 1]);
        assertThat(digestAlgorithm.isHashEmpty(longHash)).isFalse();
    }
}
