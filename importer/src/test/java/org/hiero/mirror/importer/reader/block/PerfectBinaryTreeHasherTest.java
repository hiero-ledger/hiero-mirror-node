// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.reader.block.PerfectBinaryTreeHasher.EMPTY_HASH;

import org.junit.jupiter.api.Test;

final class PerfectBinaryTreeHasherTest {

    @Test
    void empty() {
        assertThat(new PerfectBinaryTreeHasher().digest()).isEqualTo(EMPTY_HASH);
    }

    @Test
    void digestWithPadding() {
        final byte[] data = new byte[48];
        data[0] = (byte) 0x01;
        final byte[] actual = new PerfectBinaryTreeHasher()
                .addLeaf(data)
                .addLeaf(data)
                .addLeaf(data)
                .digest();
        final byte[] expected = new byte[] {
            -19, 88, 91, 84, 11, 100, -57, -44, -11, 107, 8, 105, 4, -10, -101, 60, 49, -38, 86, -58, -31, 97, -113,
            -41, 125, -86, -19, -103, 45, 32, 50, -5, 50, 78, 42, 10, -70, -128, -88, 124, 51, 15, 5, -107, 51, -79, 32,
            15
        };
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void digestWithoutPadding() {
        final byte[] data = new byte[48];
        data[0] = (byte) 0x01;
        final byte[] actual =
                new PerfectBinaryTreeHasher().addLeaf(data).addLeaf(data).digest();
        final byte[] expected = new byte[] {
            -37, 72, -51, -3, -105, 112, 102, -116, -100, 40, -19, -103, 58, -54, 95, 72, -83, -70, 78, 98, -45, -7,
            123, -116, -30, 0, 65, 49, -17, -56, -16, 0, -32, -120, 35, -116, -125, 75, -53, -25, 35, -20, 67, 30, 7,
            -45, 32, -113
        };
        assertThat(actual).isEqualTo(expected);
    }
}
