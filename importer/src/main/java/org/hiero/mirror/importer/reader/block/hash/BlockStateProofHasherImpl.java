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
import org.jspecify.annotations.NullMarked;

@Named
@NullMarked
final class BlockStateProofHasherImpl implements BlockStateProofHasher {

    private static final int DEPTH3_RIGHT_SIBLING_MODULAR_INDEX = 2;
    private static final int MIN_PREVIOUS_BLOCK_ROOT_PATH_SIBLING_COUNT = 7;
    private static final int SIBLING_GROUP_SIZE = 4;

    @Override
    public byte[] getRootHash(
            final long blockNumber, final byte[] currentRootHash, final List<MerklePath> merklePaths) {
        if (merklePaths.size() != 3) {
            throw new InvalidStreamFileException(
                    "Number of merkle paths in block %d's StateProof is not 3".formatted(blockNumber));
        }

        final var depth1RightPath = merklePaths.get(1);
        // Sibling nodes are grouped in 4, with the last 3 and the timestamp leaf in the first merkle path forming
        // the last group. Since the second merkle path is from the previous block root, there should be at least
        // 7 sibling nodes: the hash and the first 4 sibling nodes lead to the current block's root, the next 3
        // sibling nodes and the timestamp leaf then lead to the block's root the TSS signature is for.
        final var siblings = depth1RightPath.getSiblingsList();
        if (siblings.size() < MIN_PREVIOUS_BLOCK_ROOT_PATH_SIBLING_COUNT) {
            throw new InvalidStreamFileException(
                    "Block %d's merkle path from the previous block root has less than %d siblings"
                            .formatted(blockNumber, MIN_PREVIOUS_BLOCK_ROOT_PATH_SIBLING_COUNT));
        }

        final var digest = createSha384Digest();
        byte[] hash = toBytes(depth1RightPath.getHash());
        for (int i = 0; i < siblings.size(); i++) {
            final var sibling = siblings.get(i);
            final byte[] siblingHash = toBytes(sibling.getHash());
            final byte[] leftChild = sibling.getIsLeft() ? siblingHash : hash;
            final byte[] rightChild = !sibling.getIsLeft() ? siblingHash : hash;
            hash = HashUtils.hashInternalNode(digest, leftChild, rightChild);

            if (i % SIBLING_GROUP_SIZE == DEPTH3_RIGHT_SIBLING_MODULAR_INDEX) {
                // We get the depth2 left node's hash after processing the depth3 right sibling node. The depth2 left
                // node is the depth1 right node's single child, and it's implicit. Directly calculate the node's hash
                hash = HashUtils.hashInternalNode(digest, hash);
            }

            if (i == SIBLING_GROUP_SIZE - 1) {
                // End of the first 4-sibling group, the hash should match the current root hash
                if (!Arrays.equals(hash, currentRootHash)) {
                    throw new InvalidStreamFileException("Block %d root hash mismatch: expected=%s, actual=%s"
                            .formatted(blockNumber, Hex.encodeHexString(currentRootHash), Hex.encodeHexString(hash)));
                }
            }
        }

        final byte[] depth1Left =
                HashUtils.hashLeaf(digest, toBytes(merklePaths.getFirst().getTimestampLeaf()));
        return HashUtils.hashInternalNode(digest, depth1Left, hash);
    }
}
