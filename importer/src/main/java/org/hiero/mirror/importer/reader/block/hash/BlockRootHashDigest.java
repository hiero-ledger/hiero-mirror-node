// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.hash;

import com.hedera.hapi.block.stream.output.protoc.BlockFooter;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.hiero.block.internal.protoc.BlockItemUnparsed;
import org.hiero.mirror.common.util.DomainUtils;

public final class BlockRootHashDigest {

    // Slots 0-7 carry the block's subtree roots, slots 8-15 are reserved for future extension
    private static final int SLOT_COUNT = 16;

    private final List<BlockItemUnparsed> consensusHeaders = new ArrayList<>();
    private final List<BlockItemUnparsed> inputs = new ArrayList<>();
    private final List<BlockItemUnparsed> outputs = new ArrayList<>();
    private final List<BlockItemUnparsed> stateChanges = new ArrayList<>();
    private final List<BlockItemUnparsed> traceDatum = new ArrayList<>();

    private boolean finalized;

    public void addBlockItem(final BlockItemUnparsed blockItem) {
        if (finalized) {
            throw new IllegalStateException("Can't add more block items once finalized");
        }

        final var blockItems = switch (blockItem.getItemCase()) {
            case EVENT_HEADER, ROUND_HEADER -> consensusHeaders;
            case BLOCK_HEADER, RECORD_FILE, TRANSACTION_OUTPUT, TRANSACTION_RESULT -> outputs;
            case SIGNED_TRANSACTION -> inputs;
            case STATE_CHANGES -> stateChanges;
            case TRACE_DATA -> traceDatum;
            default -> null;
        };

        if (blockItems != null) {
            blockItems.add(blockItem);
        }
    }

    /**
     * Calculates the block root hash from the block items added, the block timestamp and the block footer.
     *
     * @param blockTimestamp the block timestamp in the block header
     * @param blockFooter    the block footer
     * @return the block root hash
     */
    public byte[] digest(final Timestamp blockTimestamp, final BlockFooter blockFooter) {
        final byte[] previousHash = DomainUtils.toBytes(blockFooter.getPreviousBlockRootHash());
        final int digestSize = previousHash.length;
        final var slots = new ArrayList<byte[]>(SLOT_COUNT);
        slots.add(previousHash);
        slots.add(DomainUtils.toBytes(blockFooter.getRootHashOfAllBlockHashesTree()));
        slots.add(DomainUtils.toBytes(blockFooter.getStartOfBlockStateRootHash()));
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

    private byte[] computeBlockItemSubTreeRootHash(final List<BlockItemUnparsed> blockItems, final int digestSize) {
        final var hasher = new IncrementalStreamingHasher(digestSize);
        for (int i = 0; i < blockItems.size(); i++) {
            // A BlockItemUnparsed keeps its item as the serialized bytes, so serializing it reproduces the block item
            // as produced. Re-serializing a parsed BlockItem may not, e.g., when it has fields unknown to this version
            // of the protobuf classes
            hasher.addLeaf(blockItems.get(i).toByteArray());
        }
        return hasher.computeRootHash();
    }
}
