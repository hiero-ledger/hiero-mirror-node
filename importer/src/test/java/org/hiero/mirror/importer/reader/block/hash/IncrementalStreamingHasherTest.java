// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.hash;

import static org.assertj.core.api.Assertions.assertThat;

import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

final class IncrementalStreamingHasherTest {

    static final byte[] EMPTY_TREE_HASH = Hex.decode(
            "bec021b4f368e3069134e012c2b4307083d3a9bdd206e24e5f0d86e13d6636655933ec2b413465966817a9c208a11717");

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

    @Tag("Conformance constants")
    @Test
    void hashEmptyTree() {
        final var hasher = new IncrementalStreamingHasher();
        assertThat(hasher.computeRootHash()).isEqualTo(EMPTY_TREE_HASH);
    }
}
