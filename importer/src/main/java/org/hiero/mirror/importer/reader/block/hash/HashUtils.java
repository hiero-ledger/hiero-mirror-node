// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.hash;

import java.security.MessageDigest;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.NullMarked;

@NullMarked
@UtilityClass
final class HashUtils {

    private static final byte[] FULL_INTERNAL_NODE_PREFIX = {0x2};
    private static final byte[] LEAF_PREFIX = {0x0};
    private static final byte[] SINGLE_CHILD_INTERNAL_NODE_PREFIX = {0x1};

    public static byte[] hashInternalNode(final MessageDigest digest, final byte[] leftChild) {
        digest.update(SINGLE_CHILD_INTERNAL_NODE_PREFIX);
        return digest.digest(leftChild);
    }

    public static byte[] hashInternalNode(final MessageDigest digest, final byte[] leftChild, final byte[] rightChild) {
        digest.update(FULL_INTERNAL_NODE_PREFIX);
        digest.update(leftChild);
        return digest.digest(rightChild);
    }

    public static byte[] hashLeaf(final MessageDigest digest, final byte[] leafData) {
        digest.update(LEAF_PREFIX);
        return digest.digest(leafData);
    }
}
