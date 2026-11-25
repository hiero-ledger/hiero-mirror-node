// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.hash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.createSha256Digest;

import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

final class IncrementalStreamingHasherTest {

    static final byte[] EMPTY_TREE_HASH = createSha256Digest().digest(new byte[] {0x0});

    @Test
    void hash() {
        // given
        final var hasher = new IncrementalStreamingHasher();
        hasher.addLeaf(new byte[] {0x0});
        hasher.addLeaf(new byte[] {0x1});
        hasher.addLeaf(new byte[] {0x2});

        // when, then
        assertThat(hasher.computeRootHash())
                .isEqualTo(Hex.decode("4d10054b665bb1695e7c1c633272cc0686a901bfcef0978a485f610dfe66cb0e"));
    }

    @Tag("Conformance constants")
    @Test
    void hashEmptyTree() {
        final var hasher = new IncrementalStreamingHasher();
        assertThat(hasher.computeRootHash()).isEqualTo(EMPTY_TREE_HASH);
        assertThat(IncrementalStreamingHasher.getEmptyTreeHash()).isEqualTo(EMPTY_TREE_HASH);
    }
}
