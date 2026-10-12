// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.hash;

import static org.assertj.core.api.Assertions.assertThat;

import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

final class HashUtilsTest {

    @ParameterizedTest(name = "digest size {0}")
    @CsvSource(textBlock = """
            32, 4bf5122f344554c53bde2ebb8cd2b7e3d1600ad631c385a5d7cce23c7785459a, dbc1b4c900ffe48d575b5da5c638040125f65db0fe3e24494b76ea986457d986
            48, 8d2ce87d86f55fcfab770a047b090da23270fa206832dfea7e0c946fff451f819add242374be551b0d6318ed6c7d41d8, db475240477c5e4497a2c5724ca485f5b1f2c2fc0602b92bae234238ec8d4e873a7148c739593e95a7f4dfe7c6e69f69
            """)
    void hashInternalNode(final int digestSize, final String expectedSingleChild, final String expectedTwoChildren) {
        final var digest = ShaMessageDigestFactory.createMessageDigest(digestSize);
        assertThat(HashUtils.hashInternalNode(digest, new byte[0])).isEqualTo(Hex.decode(expectedSingleChild));
        assertThat(HashUtils.hashInternalNode(digest, new byte[0], new byte[0]))
                .isEqualTo(Hex.decode(expectedTwoChildren));
    }

    @ParameterizedTest(name = "digest size {0}")
    @CsvSource(textBlock = """
            32, 6e340b9cffb37a989ca544e6bb780a2c78901d3fb33738768511a30617afa01d
            48, bec021b4f368e3069134e012c2b4307083d3a9bdd206e24e5f0d86e13d6636655933ec2b413465966817a9c208a11717
            """)
    void hashLeaf(final int digestSize, final String expected) {
        final var digest = ShaMessageDigestFactory.createMessageDigest(digestSize);
        assertThat(HashUtils.hashLeaf(digest, new byte[0])).isEqualTo(Hex.decode(expected));
    }
}
