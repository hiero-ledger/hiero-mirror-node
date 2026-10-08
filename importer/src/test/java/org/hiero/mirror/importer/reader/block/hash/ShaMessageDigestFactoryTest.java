// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.hash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

final class ShaMessageDigestFactoryTest {

    @ParameterizedTest
    @CsvSource(textBlock = """
            32, E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855
            48, 38B060A751AC96384CD9327EB1B1E36A21FDB71114BE07434C0CC7BF63F6E1DA274EDEBFE76F65FBD51AD2F14898B95B
            """)
    void createAndDigest(final int digestSize, final String expectedDigest) {
        assertThat(ShaMessageDigestFactory.createMessageDigest(digestSize).digest())
                .asHexString()
                .isEqualTo(expectedDigest);
    }

    @Test
    void unsupportedDigestSize() {
        assertThatThrownBy(() -> ShaMessageDigestFactory.createMessageDigest(12))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported digest size 12");
    }
}
