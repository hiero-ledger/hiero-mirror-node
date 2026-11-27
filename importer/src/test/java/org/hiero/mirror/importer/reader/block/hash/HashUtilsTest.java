// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.hash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.createSha256Digest;

import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

final class HashUtilsTest {

    @Test
    void hashInternalNode() {
        final var digest = createSha256Digest();
        assertThat(HashUtils.hashInternalNode(digest, new byte[0]))
                .isEqualTo(Hex.decode("4bf5122f344554c53bde2ebb8cd2b7e3d1600ad631c385a5d7cce23c7785459a"));
        assertThat(HashUtils.hashInternalNode(digest, new byte[0], new byte[0]))
                .isEqualTo(Hex.decode("dbc1b4c900ffe48d575b5da5c638040125f65db0fe3e24494b76ea986457d986"));
    }

    @Test
    void hashLeaf() {
        assertThat(HashUtils.hashLeaf(createSha256Digest(), new byte[0]))
                .isEqualTo(Hex.decode("6e340b9cffb37a989ca544e6bb780a2c78901d3fb33738768511a30617afa01d"));
    }
}
