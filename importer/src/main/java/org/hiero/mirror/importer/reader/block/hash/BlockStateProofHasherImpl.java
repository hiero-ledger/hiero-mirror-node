// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.hash;

import static org.hiero.mirror.common.util.DomainUtils.createSha384Digest;
import static org.hiero.mirror.common.util.DomainUtils.toBytes;

import com.hedera.hapi.block.stream.protoc.MerklePath;
import jakarta.inject.Named;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.codec.binary.Hex;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;

@Named
final class BlockStateProofHasherImpl implements BlockStateProofHasher {

    // The merkle path 1 carries the current block's root hash and the sibling nodes leading up to the signed block's
    // depth1 right child node. The signed block's timestamp leaf lives in the first merkle path (index 0).
    private static final int BLOCK_CONTENTS_PATH_INDEX = 1;
    // Each block contributes 3 real sibling nodes plus a null-hash sentinel for the single-child internal node wrap.
    // The signed block contributes only those (its timestamp is in the first merkle path), so the minimum sibling
    // count is 4, corresponding to the current block being directly proven by the immediately following signed block.
    private static final int MIN_SIBLING_COUNT = 4;

    @Override
    public byte[] getRootHash(
            final long blockNumber, final byte[] currentRootHash, final List<MerklePath> merklePaths) {
        if (merklePaths.size() != 3) {
            throw new InvalidStreamFileException(
                    "Number of merkle paths in block %d's StateProof is not 3".formatted(blockNumber));
        }

        // The merkle path 1 starts from the current block's own root hash, which must match the hash independently
        // computed for the block.
        final var blockContentsPath = merklePaths.get(BLOCK_CONTENTS_PATH_INDEX);
        byte[] hash = toBytes(blockContentsPath.getHash());
        if (!Arrays.equals(hash, currentRootHash)) {
            throw new InvalidStreamFileException("Block %d root hash mismatch: expected=%s, actual=%s"
                    .formatted(blockNumber, Hex.encodeHexString(currentRootHash), Hex.encodeHexString(hash)));
        }

        final var siblings = blockContentsPath.getSiblingsList();
        if (siblings.size() < MIN_SIBLING_COUNT) {
            throw new InvalidStreamFileException("Block %d's block contents merkle path has less than %d siblings"
                    .formatted(blockNumber, MIN_SIBLING_COUNT));
        }

        // Walk the sibling nodes up the tree. Starting from the current block's root hash, each subsequent block
        // contributes its right sibling nodes, a null-hash sentinel encoding the single-child internal node wrap,
        // and (for intermediate blocks only) its timestamp as a left sibling to reach that block's root - which in
        // turn is the left-most leaf of the next block. The last block is the signed block, whose timestamp is
        // instead applied below via the first merkle path.
        final var digest = createSha384Digest();
        for (final var sibling : siblings) {
            final byte[] siblingHash = toBytes(sibling.getHash());
            if (siblingHash.length == 0) {
                // Null-hash sentinel: apply the single-child internal node wrap.
                hash = HashUtils.hashInternalNode(digest, hash);
            } else if (sibling.getIsLeft()) {
                hash = HashUtils.hashInternalNode(digest, siblingHash, hash);
            } else {
                hash = HashUtils.hashInternalNode(digest, hash, siblingHash);
            }
        }

        final byte[] depth1Left =
                HashUtils.hashLeaf(digest, toBytes(merklePaths.getFirst().getTimestampLeaf()));
        return HashUtils.hashInternalNode(digest, depth1Left, hash);
    }
}
