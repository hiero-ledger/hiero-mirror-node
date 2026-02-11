// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.hash;

import java.security.MessageDigest;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.NullMarked;

@NullMarked
@UtilityClass
final class HashUtils {

    private static final byte[] INTERNAL_NODE_PREFIX = {0x2};
    private static final byte[] LEAF_PREFIX = {0x0};
    private static final byte[] SINGLE_CHILD_INTERNAL_NODE_PREFIX = {0x1};

    public static byte[] hashInternalNode(final MessageDigest digest, final byte[]... leaves) {
        if (leaves.length != 1 && leaves.length != 2) {
            throw new IllegalArgumentException("There must be one or two leaves to calculate the parent's hash");
        }

        return leaves.length == 1
                ? hashOfAll(digest, SINGLE_CHILD_INTERNAL_NODE_PREFIX, leaves[0])
                : hashOfAll(digest, INTERNAL_NODE_PREFIX, leaves[0], leaves[1]);
    }

    public static byte[] hashLeaf(final MessageDigest digest, final byte[] leafData) {
        return hashOfAll(digest, LEAF_PREFIX, leafData);
    }

    private static byte[] hashOfAll(final MessageDigest digest, final byte[]... bytes) {
        for (final var chunk : bytes) {
            digest.update(chunk);
        }
        return digest.digest();
    }
}
