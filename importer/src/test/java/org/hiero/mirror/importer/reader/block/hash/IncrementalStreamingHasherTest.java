// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.hash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.createSha384Digest;

import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

final class IncrementalStreamingHasherTest {

    @Test
    void hash() {
        // given
        final var hasher = new IncrementalStreamingHasher();
        hasher.addLeaf(new byte[] {0x0});
        hasher.addLeaf(new byte[] {0x1});
        hasher.addLeaf(new byte[] {0x2});

        // when, then
        assertThat(hasher.computeRootHash())
                .isEqualTo(
                        Hex.decode(
                                "c84d5ef5565ebd554d692d4a9500c7f328f05c0a661cc627a036dcb84f6563a27ceabf32fdf70c77e4c527f7490f2fa8"));
    }

    @Test
    void hashEmptyTree() {
        final var hasher = new IncrementalStreamingHasher();
        assertThat(hasher.computeRootHash()).isEqualTo(createSha384Digest().digest(new byte[] {0x0}));
    }
}
