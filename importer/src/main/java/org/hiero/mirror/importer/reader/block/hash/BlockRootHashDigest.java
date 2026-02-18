// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.hash;

import static org.hiero.mirror.common.util.DomainUtils.createSha384Digest;

import com.hedera.hapi.block.stream.protoc.BlockItem;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.security.MessageDigest;
import org.hiero.mirror.common.util.DomainUtils;
import org.jspecify.annotations.NonNull;

public final class BlockRootHashDigest {

    private final IncrementalStreamingHasher consensusHeaderHasher = new IncrementalStreamingHasher();
    private final MessageDigest digest = createSha384Digest();
    private final IncrementalStreamingHasher inputHasher = new IncrementalStreamingHasher();
    private final IncrementalStreamingHasher outputHasher = new IncrementalStreamingHasher();
    private final IncrementalStreamingHasher stateChangesHasher = new IncrementalStreamingHasher();
    private final IncrementalStreamingHasher traceDataHasher = new IncrementalStreamingHasher();

    private Timestamp blockTimestamp;
    private boolean finalized;
    private byte[] previousBlocksTreeHash;
    private byte[] previousHash;
    private byte[] startOfBlockStateHash;

    public void addBlockItem(final @NonNull BlockItem blockItem) {
        if (finalized) {
            throw new IllegalStateException("Can't add more block items once finalized");
        }

        final var hasher =
                switch (blockItem.getItemCase()) {
                    case BLOCK_HEADER -> {
                        blockTimestamp = blockItem.getBlockHeader().getBlockTimestamp();
                        yield outputHasher;
                    }
                    case BLOCK_FOOTER -> {
                        final var blockFooter = blockItem.getBlockFooter();
                        previousBlocksTreeHash = DomainUtils.toBytes(blockFooter.getRootHashOfAllBlockHashesTree());
                        previousHash = DomainUtils.toBytes(blockFooter.getPreviousBlockRootHash());
                        startOfBlockStateHash = DomainUtils.toBytes(blockFooter.getStartOfBlockStateRootHash());
                        yield null;
                    }
                    case EVENT_HEADER, ROUND_HEADER -> consensusHeaderHasher;
                    case TRANSACTION_OUTPUT, TRANSACTION_RESULT -> outputHasher;
                    case SIGNED_TRANSACTION -> inputHasher;
                    case STATE_CHANGES -> stateChangesHasher;
                    case TRACE_DATA -> traceDataHasher;
                    default -> null;
                };

        if (hasher != null) {
            hasher.addLeaf(blockItem.toByteArray());
        }
    }

    public byte[] digest() {
        if (blockTimestamp == null
                || previousBlocksTreeHash == null
                || previousHash == null
                || startOfBlockStateHash == null) {
            throw new IllegalStateException(
                    "blockTimestamp / previousBlocksTreeHash / previousHash / startOfBlockStateHash are not set");
        }

        // Block merkle tree:
        //                 Root
        //                /    \
        //               t   [internal]
        //                      /
        //                [internal]
        //             _______/ \_______
        //            /                 \
        //        [internal]         [internal]
        //         /    \             /       \
        //  [internal] [internal] [internal] [internal]
        //       /  \     /  \       /  \       /  \
        //      L1  L2   L3  L4     L5  L6     L7  L8
        // t  - The block timestamp leaf
        // L1 - Previous block root hash
        // L2 - Root of tree of all previous block hashes
        // L3 - Root of state at start of block
        // L4 - Root of consensus headers
        // L5 - Root of input block items
        // L6 - Root of output block items
        // L7 - Root of state change block items
        // L8 - Root of trace data block items
        // Note the depth1 right node doesn't have a right child node, by design it's reserved for future extension.
        final byte[] depth2Left = combine(
                previousHash,
                previousBlocksTreeHash,
                startOfBlockStateHash,
                consensusHeaderHasher.computeRootHash(),
                inputHasher.computeRootHash(),
                outputHasher.computeRootHash(),
                stateChangesHasher.computeRootHash(),
                traceDataHasher.computeRootHash());
        final byte[] depth1Right = HashUtils.hashInternalNode(digest, depth2Left);
        final byte[] depth1Left = HashUtils.hashLeaf(digest, blockTimestamp.toByteArray());

        final var rootHash = combine(depth1Left, depth1Right);
        finalized = true;

        return rootHash;
    }

    private byte[] combine(final byte[]... leaves) {
        int size = leaves.length;
        if (size == 0 || (size & (size - 1)) != 0) {
            throw new IllegalArgumentException("The leaves must be non-empty and the count must be a power of 2");
        }

        while (size > 1) {
            for (int i = 0; i < size >> 1; i++) {
                final byte[] internal = HashUtils.hashInternalNode(digest, leaves[2 * i], leaves[2 * i + 1]);
                leaves[i] = internal;
            }

            size = size >> 1;
        }

        return leaves[0];
    }
}
