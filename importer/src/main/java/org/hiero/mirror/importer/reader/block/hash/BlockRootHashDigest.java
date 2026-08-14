// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.hash;

import static org.hiero.mirror.common.util.DomainUtils.createSha384Digest;
import static org.hiero.mirror.importer.reader.block.hash.IncrementalStreamingHasher.EMPTY_TREE_HASH;

import com.hedera.hapi.block.stream.protoc.BlockItem;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.hiero.mirror.common.domain.DigestAlgorithm;
import org.hiero.mirror.common.util.DomainUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullUnmarked;

@NullUnmarked
public final class BlockRootHashDigest {

    private static final int HASH_LENGTH = DigestAlgorithm.SHA_384.getSize();
    // Slots 0-7 carry the block's subtree roots, slots 8-15 are reserved for future extension
    private static final int RESERVED_SLOT_COUNT = 8;
    private static final int SLOT_COUNT = 16;

    private final IncrementalStreamingHasher consensusHeaderHasher = new IncrementalStreamingHasher();
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
                    case RECORD_FILE, TRANSACTION_OUTPUT, TRANSACTION_RESULT -> outputHasher;
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

        final var slots = new ArrayList<byte[]>(SLOT_COUNT);
        slots.add(validate(previousHash, 0));
        slots.add(validate(previousBlocksTreeHash, 1));
        slots.add(validate(startOfBlockStateHash, 2));
        slots.add(consensusHeaderHasher.computeRootHash());
        slots.add(inputHasher.computeRootHash());
        slots.add(outputHasher.computeRootHash());
        slots.add(stateChangesHasher.computeRootHash());
        slots.add(traceDataHasher.computeRootHash());
        appendReservedSlots(slots);

        final byte[] streamedRootHash = streamedRootOf(slots);
        final var digest = createSha384Digest();
        final byte[] timestampLeaf = HashUtils.hashLeaf(digest, blockTimestamp.toByteArray());
        final byte[] rootHash = HashUtils.hashInternalNode(digest, timestampLeaf, streamedRootHash);
        finalized = true;
        return rootHash;
    }

    static byte[] streamedRootOf(final List<byte[]> slots) {
        final var hasher = new IncrementalStreamingHasher();
        for (final byte[] slot : slots) {
            hasher.addNodeByHash(slot);
        }
        return hasher.computeRootHash();
    }

    private static void appendReservedSlots(final List<byte[]> slots) {
        for (int i = 0; i < RESERVED_SLOT_COUNT; i++) {
            slots.add(EMPTY_TREE_HASH);
        }
    }

    private static byte[] validate(final byte[] hash, final int slot) {
        if (hash == null || hash.length != HASH_LENGTH) {
            final int length = hash != null ? hash.length : 0;
            throw new IllegalStateException(
                    "Block root tree slot %d is %d bytes, expected %d".formatted(slot, length, HASH_LENGTH));
        }

        return hash;
    }
}
