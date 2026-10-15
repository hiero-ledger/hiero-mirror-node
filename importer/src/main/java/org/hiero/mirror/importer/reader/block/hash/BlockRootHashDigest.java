// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.hash;

import com.hedera.hapi.block.stream.protoc.BlockItem;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.hiero.mirror.common.util.DomainUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullUnmarked;

@NullUnmarked
public final class BlockRootHashDigest {

    // Slots 0-7 carry the block's subtree roots, slots 8-15 are reserved for future extension
    private static final int SLOT_COUNT = 16;

    private final List<BlockItem> consensusHeaders = new ArrayList<>();
    private final List<BlockItem> inputs = new ArrayList<>();
    private final List<BlockItem> outputs = new ArrayList<>();
    private final List<BlockItem> stateChanges = new ArrayList<>();
    private final List<BlockItem> traceDatum = new ArrayList<>();

    private Timestamp blockTimestamp;
    private boolean finalized;
    private byte[] previousBlocksTreeHash;
    private byte[] previousHash;
    private byte[] startOfBlockStateHash;

    public void addBlockItem(final @NonNull BlockItem blockItem) {
        if (finalized) {
            throw new IllegalStateException("Can't add more block items once finalized");
        }

        final var blockItems =
                switch (blockItem.getItemCase()) {
                    case BLOCK_HEADER -> {
                        blockTimestamp = blockItem.getBlockHeader().getBlockTimestamp();
                        yield outputs;
                    }
                    case BLOCK_FOOTER -> {
                        final var blockFooter = blockItem.getBlockFooter();
                        previousBlocksTreeHash = DomainUtils.toBytes(blockFooter.getRootHashOfAllBlockHashesTree());
                        previousHash = DomainUtils.toBytes(blockFooter.getPreviousBlockRootHash());
                        startOfBlockStateHash = DomainUtils.toBytes(blockFooter.getStartOfBlockStateRootHash());
                        yield null;
                    }
                    case EVENT_HEADER, ROUND_HEADER -> consensusHeaders;
                    case RECORD_FILE, TRANSACTION_OUTPUT, TRANSACTION_RESULT -> outputs;
                    case SIGNED_TRANSACTION -> inputs;
                    case STATE_CHANGES -> stateChanges;
                    case TRACE_DATA -> traceDatum;
                    default -> null;
                };

        if (blockItems != null) {
            blockItems.add(blockItem);
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

        final int digestSize = previousHash.length;
        final var slots = new ArrayList<byte[]>(SLOT_COUNT);
        slots.add(previousHash);
        slots.add(previousBlocksTreeHash);
        slots.add(startOfBlockStateHash);
        slots.add(computeBlockItemSubTreeRootHash(consensusHeaders, digestSize));
        slots.add(computeBlockItemSubTreeRootHash(inputs, digestSize));
        slots.add(computeBlockItemSubTreeRootHash(outputs, digestSize));
        slots.add(computeBlockItemSubTreeRootHash(stateChanges, digestSize));
        slots.add(computeBlockItemSubTreeRootHash(traceDatum, digestSize));
        appendReservedSlots(digestSize, slots);

        final byte[] streamedRootHash = streamedRootOf(digestSize, slots);
        final var digest = ShaMessageDigestFactory.createMessageDigest(digestSize);
        final byte[] timestampLeaf = HashUtils.hashLeaf(digest, blockTimestamp.toByteArray());
        final byte[] rootHash = HashUtils.hashInternalNode(digest, timestampLeaf, streamedRootHash);
        finalized = true;
        return rootHash;
    }

    static byte[] streamedRootOf(final int digestSize, final List<byte[]> slots) {
        final var hasher = new IncrementalStreamingHasher(digestSize);
        for (final byte[] slot : slots) {
            hasher.addNodeByHash(slot);
        }
        return hasher.computeRootHash();
    }

    private void appendReservedSlots(final int digestSize, final List<byte[]> slots) {
        final var emptyHash = IncrementalStreamingHasher.getEmptyTreeHash(digestSize);
        while (slots.size() < SLOT_COUNT) {
            slots.add(emptyHash);
        }
    }

    private byte[] computeBlockItemSubTreeRootHash(final List<BlockItem> blockItems, final int digestSize) {
        final var hasher = new IncrementalStreamingHasher(digestSize);
        for (int i = 0; i < blockItems.size(); i++) {
            hasher.addLeaf(blockItems.get(i).toByteArray());
        }
        return hasher.computeRootHash();
    }
}
