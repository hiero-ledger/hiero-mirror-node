// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.hash;

import static org.hiero.mirror.common.util.DomainUtils.createSha384Digest;
import static org.hiero.mirror.importer.reader.block.hash.HashUtils.hashLeaf;

import java.security.MessageDigest;
import java.util.LinkedList;
import java.util.List;
import org.jspecify.annotations.NullMarked;

/**
 * A memory-efficient Merkle tree hasher that computes root hashes in a streaming fashion.
 *
 * <h2>Thread Safety</h2>
 * <p>This class is NOT thread-safe. It is designed for single-threaded use.
 *
 * <p>This class is based on Hiero Consensus Node's {@code IncrementalStreamingHasher}, located at
 * <a href="https://github.com/hiero-ledger/hiero-consensus-node/blob/main/hedera-node/hedera-app/src/main/java/com/hedera/node/app/blocks/impl/IncrementalStreamingHasher.java">this link</a>.
 *
 */
@NullMarked
final class IncrementalStreamingHasher {

    private static final byte[] HASH_OF_ZERO_BYTES = createSha384Digest().digest(new byte[] {0x0});

    /** The hashing algorithm used for computing the hashes. */
    private final MessageDigest digest = createSha384Digest();
    /** A list to store intermediate hashes as we build the tree. */
    private final List<byte[]> hashList = new LinkedList<>();
    /** The count of leaves in the tree. */
    private long leafCount;

    /**
     * Adds a new leaf to the Merkle tree.
     *
     * <p>The leaf data is hashed with prefix {@code 0x00}, then the streaming fold-up
     * algorithm combines any complete sibling pairs into internal nodes.
     *
     * <p>Time complexity: O(log n) hash operations in the worst case, O(1) amortized.
     *
     * @param data the raw data for the new leaf
     */
    public void addLeaf(final byte[] data) {
        addNodeByHash(hashLeaf(digest, data));
    }

    /**
     * Computes the Merkle tree root hash from the current state.
     *
     * <p>This method folds all pending subtree roots from right to left to produce
     * the final root hash. The internal state is not modified, so more leaves can
     * be added after calling this method.
     *
     * <p>Time complexity: O(log n) where n is the leaf count.
     *
     * <p>For an empty tree (no leaves added), this method returns the predefined zero-bytes hash
     * which is {@code sha384Hash(new byte[]{0x00})}.
     *
     * @return the 48-byte SHA-384 Merkle tree root hash, or the zero-bytes hash
     *         if no leaves have been added
     */
    public byte[] computeRootHash() {
        if (hashList.isEmpty()) {
            // This value is precomputed as the hash of an empty tree; therefore it should _not_ be hashed as a leaf
            return HASH_OF_ZERO_BYTES;
        }

        if (hashList.size() == 1) {
            // This value should already have been hashed as a leaf, and therefore should _not_ be re-hashed
            return hashList.getFirst();
        }

        byte[] merkleRootHash = hashList.getLast();
        for (int i = hashList.size() - 2; i >= 0; i--) {
            merkleRootHash = hashInternalNode(hashList.get(i), merkleRootHash);
        }
        return merkleRootHash;
    }

    /**
     * Add a pre-hashed node to the Merkle tree. This is needed for a tree of other trees. Where each node at the
     * bottom of this tree is the root hash of another tree.
     *
     * @param hash the 48-byte SHA-384 hash of the node to add (must already include the prefixing)
     */
    private void addNodeByHash(byte[] hash) {
        hashList.add(hash);
        // Fold up: combine sibling pairs while the current position is odd
        for (long n = leafCount; (n & 1L) == 1; n >>= 1) {
            final byte[] y = hashList.removeLast();
            final byte[] x = hashList.removeLast();
            hashList.add(hashInternalNode(x, y));
        }
        leafCount++;
    }

    /**
     * Hash an internal node by combining the hashes of its two children with the appropriate prefix.
     *
     * @param firstChild the hash of the first child
     * @param secondChild the hash of the second child
     * @return the hash of the internal node
     */
    private byte[] hashInternalNode(final byte[] firstChild, final byte[] secondChild) {
        return HashUtils.hashInternalNode(digest, firstChild, secondChild);
    }
}
