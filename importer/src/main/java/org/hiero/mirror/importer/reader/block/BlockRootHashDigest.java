// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block;

import static org.hiero.mirror.common.util.DomainUtils.createSha384Digest;

import com.hedera.hapi.block.stream.protoc.BlockItem;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.security.MessageDigest;
import org.bouncycastle.util.encoders.Hex;
import org.hiero.mirror.common.domain.DigestAlgorithm;
import org.hiero.mirror.common.util.DomainUtils;
import org.jspecify.annotations.NonNull;

public final class BlockRootHashDigest {

    private static final byte[] DEPTH2_RIGHT;

    private final PerfectBinaryTreeHasher consensusHeaderHasher = new PerfectBinaryTreeHasher();
    private final MessageDigest digest = createSha384Digest();
    private final PerfectBinaryTreeHasher inputHasher = new PerfectBinaryTreeHasher();
    private final PerfectBinaryTreeHasher outputHasher = new PerfectBinaryTreeHasher();
    private final PerfectBinaryTreeHasher stateChangesHasher = new PerfectBinaryTreeHasher();
    private final PerfectBinaryTreeHasher traceDataHasher = new PerfectBinaryTreeHasher();

    private Timestamp blockTimestamp;
    private boolean finalized;
    private byte[] previousBlocksTreeHash;
    private byte[] previousHash;
    private byte[] startOfBlockStateHash;

    static {
        // Pre-compute the root hash of the subtree containing 8 reserved leaves for future extension. The default hash
        // for the reserved leaves is all 0s, a.k.a., nullHash.
        final byte[] nullHash = new byte[DigestAlgorithm.SHA_384.getSize()];
        final byte[] depth4Hash = combine(nullHash, nullHash);
        final byte[] depth3Hash = combine(depth4Hash, depth4Hash);
        DEPTH2_RIGHT = combine(depth3Hash, depth3Hash);
    }

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
            hasher.addLeaf(digest.digest(blockItem.toByteArray()));
        }
    }

    public String digest() {
        if (blockTimestamp == null
                || previousBlocksTreeHash == null
                || previousHash == null
                || startOfBlockStateHash == null) {
            throw new IllegalStateException(
                    "blockTimestamp / previousBlocksTreeHash / previousHash / startOfBlockStateHash are not set");
        }

        final byte[] depth2Left = new PerfectBinaryTreeHasher()
                .addLeaf(previousHash)
                .addLeaf(previousBlocksTreeHash)
                .addLeaf(startOfBlockStateHash)
                .addLeaf(consensusHeaderHasher.digest())
                .addLeaf(inputHasher.digest())
                .addLeaf(outputHasher.digest())
                .addLeaf(stateChangesHasher.digest())
                .addLeaf(traceDataHasher.digest())
                .digest();
        final byte[] depth1Right = combine(depth2Left, DEPTH2_RIGHT);
        final byte[] depth1Left = digest.digest(blockTimestamp.toByteArray());
        final var rootHash = Hex.toHexString(combine(depth1Left, depth1Right));
        finalized = true;

        return rootHash;
    }

    private static byte[] combine(final byte[] left, final byte[] right) {
        // Per the design doc, when combining, a one-byte prefix indicating the number of leaves is added to calculate
        // the hash. Make the change when consensus node fixes #21977.
        final var digest = createSha384Digest();
        digest.update(left);
        digest.update(right);
        return digest.digest();
    }
}
