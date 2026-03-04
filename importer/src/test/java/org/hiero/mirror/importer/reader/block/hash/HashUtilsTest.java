// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.hash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.createSha384Digest;

import java.security.MessageDigest;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

final class HashUtilsTest {

    private final MessageDigest digest = createSha384Digest();

    @Test
    void hashInternalNode() {
        assertThat(HashUtils.hashInternalNode(digest, new byte[0]))
                .isEqualTo(
                        Hex.decode(
                                "8d2ce87d86f55fcfab770a047b090da23270fa206832dfea7e0c946fff451f819add242374be551b0d6318ed6c7d41d8"));
        assertThat(HashUtils.hashInternalNode(digest, new byte[0], new byte[0]))
                .isEqualTo(
                        Hex.decode(
                                "db475240477c5e4497a2c5724ca485f5b1f2c2fc0602b92bae234238ec8d4e873a7148c739593e95a7f4dfe7c6e69f69"));
    }

    @Test
    void hashLeaf() {
        assertThat(HashUtils.hashLeaf(digest, new byte[0]))
                .isEqualTo(
                        Hex.decode(
                                "bec021b4f368e3069134e012c2b4307083d3a9bdd206e24e5f0d86e13d6636655933ec2b413465966817a9c208a11717"));
    }
}
