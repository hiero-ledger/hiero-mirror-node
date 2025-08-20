// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block;

import static org.hiero.mirror.common.domain.DigestAlgorithm.SHA_384;
import static org.hiero.mirror.common.util.DomainUtils.createSha384Digest;

import com.hedera.hapi.block.stream.protoc.BlockItem;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.hiero.mirror.common.util.DomainUtils;

/**
 * Calculates a block's root hash per the algorithm defined in HIP-1056. Note all merkle subtrees are padded with
 * SHA2-384 hash of an empty bytearray to be perfect binary trees. Note none of the methods are reentrant.
 */
public final class BlockRootHashDigest {

    static final byte[] EMPTY_HASH = createSha384Digest().digest(new byte[0]);

    private static final byte[] NULL_HASH = new byte[SHA_384.getSize()];

    private final List<byte[]> consensusHeaderHashes = new ArrayList<>();
    private final MessageDigest digest = createSha384Digest();
    private boolean finalized;
    private final List<byte[]> inputHashes = new ArrayList<>();
    private final List<byte[]> outputHashes = new ArrayList<>();
    private byte[] previousHash;
    private byte[] startOfBlockStateHash;
    private final List<byte[]> stateChangeHashes = new ArrayList<>();
    private final List<byte[]> traceDataHashes = new ArrayList<>();

    public void addBlockItem(BlockItem blockItem) {
        var subTree =
                switch (blockItem.getItemCase()) {
                    case BLOCK_HEADER, TRANSACTION_OUTPUT, TRANSACTION_RESULT -> outputHashes;
                    case EVENT_HEADER, ROUND_HEADER -> consensusHeaderHashes;
                    case SIGNED_TRANSACTION -> inputHashes;
                    case STATE_CHANGES -> stateChangeHashes;
                    case TRACE_DATA -> traceDataHashes;
                    default -> null;
                };

        if (subTree != null) {
            subTree.add(digest.digest(blockItem.toByteArray()));
        }
    }

    public String digest() {
        if (finalized) {
            throw new IllegalStateException("Block root hash is already calculated");
        }

        validateHash(previousHash, "previousHash");
        validateHash(startOfBlockStateHash, "startOfBlockStateHash");

        List<byte[]> leaves = new ArrayList<>(8);
        leaves.add(previousHash);
        leaves.add(startOfBlockStateHash);
        leaves.add(getRootHash(consensusHeaderHashes));
        leaves.add(getRootHash(inputHashes));
        leaves.add(getRootHash(outputHashes));
        leaves.add(getRootHash(stateChangeHashes));
        leaves.add(getRootHash(traceDataHashes));
        leaves.add(NULL_HASH); // root hash of extensions, there's no extension defined yet so it's just NULL_HASH

        byte[] rootHash = getRootHash(leaves);
        finalized = true;

        return DomainUtils.bytesToHex(rootHash);
    }

    public void setPreviousHash(byte[] previousHash) {
        validateHash(previousHash, "previousHash");
        this.previousHash = previousHash;
    }

    public void setStartOfBlockStateHash(byte[] startOfBlockStateHash) {
        validateHash(startOfBlockStateHash, "startOfBlockStateHash");
        this.startOfBlockStateHash = startOfBlockStateHash;
    }

    private byte[] getRootHash(List<byte[]> leaves) {
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

    private static void validateHash(byte[] hash, String name) {
        if (Objects.requireNonNull(hash, "Null " + name).length != SHA_384.getSize()) {
            throw new IllegalArgumentException(String.format("%s is not %d bytes", name, SHA_384.getSize()));
        }
    }
}
