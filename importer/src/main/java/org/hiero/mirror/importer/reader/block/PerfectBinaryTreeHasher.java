// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block;

import static org.hiero.mirror.common.util.DomainUtils.createSha384Digest;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullMarked;

/*
 * A hasher that computes the root hash of a perfect binary Merkle tree. The tree is padded with EMPTY_HASH leaves to
 * form a perfect binary tree if needed.
 */
@NullMarked
final class PerfectBinaryTreeHasher {

    static final byte[] EMPTY_HASH = createSha384Digest().digest(new byte[0]);

    private final MessageDigest digest = createSha384Digest();
    private final List<byte[]> leaves = new ArrayList<>();

    PerfectBinaryTreeHasher addLeaf(final byte[] data) {
        leaves.add(data);
        return this;
    }

    byte[] digest() {
        if (leaves.isEmpty()) {
            return EMPTY_HASH;
        }

        // Pad leaves with EMPTY_HASH to the next 2^n to form a perfect binary tree
        int size = leaves.size();
        if ((size & (size - 1)) != 0) {
            size = Integer.highestOneBit(size) << 1;
            while (leaves.size() < size) {
                leaves.add(EMPTY_HASH);
            }
        }

        // Iteratively calculate the parent node hash as h(left | right) to get the root hash in bottom-up fashion
        while (size > 1) {
            for (int i = 0; i < size; i += 2) {
                byte[] left = leaves.get(i);
                byte[] right = leaves.get(i + 1);
                digest.update(left);
                digest.update(right);
                leaves.set(i >> 1, digest.digest());
            }

            size >>= 1;
        }

        return leaves.getFirst();
    }
}
