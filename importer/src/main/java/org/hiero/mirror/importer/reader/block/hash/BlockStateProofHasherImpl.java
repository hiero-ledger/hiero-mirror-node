// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.hash;

import static org.hiero.mirror.common.util.DomainUtils.createSha384Digest;
import static org.hiero.mirror.common.util.DomainUtils.toBytes;

import com.hedera.hapi.block.stream.protoc.MerklePath;
import com.hedera.hapi.block.stream.protoc.MerklePath.ContentCase;
import jakarta.inject.Named;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import org.hiero.mirror.common.domain.DigestAlgorithm;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;

@Named
final class BlockStateProofHasherImpl implements BlockStateProofHasher {

    private static final int HASH_LENGTH = DigestAlgorithm.SHA_384.getSize();
    // A StateProof needs at least the timestamp leaf path, the current block's root hash path, and the root path
    private static final int MIN_PATH_COUNT = 3;
    // The sentinel value of MerklePath.next_path_index (UINT32_MAX) marking the root path
    private static final int ROOT_PATH_INDEX = -1;

    @Override
    public byte[] getRootHash(
            final long blockNumber, final byte[] currentRootHash, final List<MerklePath> merklePaths) {
        final int pathCount = merklePaths.size();
        if (pathCount < MIN_PATH_COUNT) {
            throw new InvalidStreamFileException("Number of merkle paths in block %d's StateProof is less than %d"
                    .formatted(blockNumber, MIN_PATH_COUNT));
        }

        final var branches = new Branch[pathCount];
        final var digest = createSha384Digest();
        final var joined = new boolean[pathCount];
        boolean foundRootHash = false;
        int joinedCount = 0;
        int joinPointCount = 0;
        int parkedCount = 0;
        byte[] rootHash = null;

        for (int index = 0; index < pathCount; index++) {
            final var path = merklePaths.get(index);
            if (!hasContent(path)) {
                // A join point is hashed when the second of the two branches below it to arrives
                joinPointCount++;
                continue;
            }

            final byte[] contentHash = getContentHash(digest, path);
            if (!foundRootHash && path.getContentCase() == ContentCase.HASH) {
                foundRootHash = Arrays.equals(contentHash, currentRootHash);
            }

            var branch = new Branch(index, foldSiblings(digest, path, contentHash));
            int pathIndex = index;
            int nextPathIndex = path.getNextPathIndex();
            boolean parked = false;

            while (!parked && nextPathIndex != ROOT_PATH_INDEX) {
                if (nextPathIndex < 0 || nextPathIndex >= pathCount) {
                    throw new InvalidStreamFileException(
                            "Block %d's StateProof has out of range next path index %d in merkle path %d"
                                    .formatted(blockNumber, nextPathIndex, pathIndex));
                }

                final var joinPath = merklePaths.get(nextPathIndex);
                if (hasContent(joinPath)) {
                    throw new InvalidStreamFileException(
                            "Block %d's StateProof has merkle path %d pointing to merkle path %d which has content"
                                    .formatted(blockNumber, pathIndex, nextPathIndex));
                }

                // Guards against both a third branch and a cycle among the join points
                if (joined[nextPathIndex]) {
                    throw new InvalidStreamFileException("Block %d's StateProof joins merkle path %d more than once"
                            .formatted(blockNumber, nextPathIndex));
                }

                final var sibling = branches[nextPathIndex];
                if (sibling == null) {
                    branches[nextPathIndex] = branch;
                    parkedCount++;
                    parked = true;
                } else {
                    branch = join(digest, joinPath, sibling, branch);
                    joined[nextPathIndex] = true;
                    joinedCount++;
                    parkedCount--;
                    pathIndex = nextPathIndex;
                    nextPathIndex = joinPath.getNextPathIndex();
                }
            }

            if (!parked) {
                if (rootHash != null) {
                    throw new InvalidStreamFileException(
                            "Block %d's StateProof has more than one root merkle path".formatted(blockNumber));
                }

                rootHash = branch.hash();
            }
        }

        if (!foundRootHash) {
            throw new InvalidStreamFileException(
                    "Block %d's StateProof has no merkle path matching the block's root hash".formatted(blockNumber));
        }

        if (rootHash == null) {
            throw new InvalidStreamFileException(
                    "Block %d's StateProof has no root merkle path".formatted(blockNumber));
        }

        // A join point left unjoined still holds the branch parked at it, if it got a child at all
        if (joinedCount != joinPointCount) {
            throw new InvalidStreamFileException(
                    parkedCount > 0
                            ? "Block %d's StateProof has a join point merkle path with only one child"
                                    .formatted(blockNumber)
                            : "Block %d's StateProof has a join point merkle path with no children"
                                    .formatted(blockNumber));
        }

        return rootHash;
    }

    private static byte[] foldSiblings(final MessageDigest digest, final MerklePath path, final byte[] startingHash) {
        byte[] hash = startingHash;
        for (final var sibling : path.getSiblingsList()) {
            final var siblingHash = sibling.getHash();
            if (siblingHash.size() != HASH_LENGTH) {
                throw new InvalidStreamFileException(
                        "Sibling hash length %d != %d".formatted(siblingHash.size(), HASH_LENGTH));
            }

            hash = sibling.getIsLeft()
                    ? HashUtils.hashInternalNode(digest, toBytes(siblingHash), hash)
                    : HashUtils.hashInternalNode(digest, hash, toBytes(siblingHash));
        }

        return hash;
    }

    private static byte[] getContentHash(final MessageDigest digest, final MerklePath path) {
        return switch (path.getContentCase()) {
            case BLOCK_ITEM_LEAF -> HashUtils.hashLeaf(digest, toBytes(path.getBlockItemLeaf()));
            case CONTENT_NOT_SET -> throw new IllegalStateException("Merkle path has no content");
            case HASH -> toBytes(path.getHash());
            case STATE_ITEM_LEAF -> HashUtils.hashLeaf(digest, toBytes(path.getStateItemLeaf()));
            case TIMESTAMP_LEAF -> HashUtils.hashLeaf(digest, toBytes(path.getTimestampLeaf()));
        };
    }

    private static boolean hasContent(final MerklePath path) {
        return path.getContentCase() != ContentCase.CONTENT_NOT_SET;
    }

    // Hashes a join point from the two branches below it, then folds the siblings the join point itself carries.
    private static Branch join(
            final MessageDigest digest, final MerklePath joinPath, final Branch first, final Branch second) {
        final var left = first.minContentIndex() < second.minContentIndex() ? first : second;
        final var right = left == first ? second : first;
        final byte[] hash = HashUtils.hashInternalNode(digest, left.hash(), right.hash());
        return new Branch(left.minContentIndex(), foldSiblings(digest, joinPath, hash));
    }

    /**
     * A partial result climbing towards the root, tagged with the lowest index of the content merkle paths beneath
     * it so that two branches meeting at a join point know which of them is the left
     */
    private record Branch(int minContentIndex, byte[] hash) {}
}
