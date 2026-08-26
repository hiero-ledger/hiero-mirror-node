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
import org.jspecify.annotations.Nullable;

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

        // The last path must be the root
        final var lastPath = merklePaths.getLast();
        if (hasContent(lastPath) || lastPath.getNextPathIndex() != ROOT_PATH_INDEX) {
            throw new InvalidStreamFileException(
                    "Block %d's StateProof does not end with the root merkle path".formatted(blockNumber));
        }

        final var children = new Children[pathCount];
        final var digest = createSha384Digest();
        boolean foundRootHash = false;

        final int rootIndex = pathCount - 1;
        for (int index = 0; index < rootIndex; index++) {
            final var path = merklePaths.get(index);
            final var pathChildren = children[index];
            final byte[] startingHash;

            if (hasContent(path)) {
                if (pathChildren != null) {
                    throw new InvalidStreamFileException(
                            "Block %d's StateProof has content in merkle path %d which is a join point"
                                    .formatted(blockNumber, index));
                }

                startingHash = getContentHash(digest, path);
                if (!foundRootHash && path.getContentCase() == ContentCase.HASH) {
                    foundRootHash = Arrays.equals(startingHash, currentRootHash);
                }
            } else {
                startingHash = joinChildren(blockNumber, digest, pathChildren, index);
            }

            // A join point can carry the siblings of the nodes above it, up to the next join point or the root
            final byte[] pathHash = foldSiblings(digest, path, startingHash);
            final int nextPathIndex = path.getNextPathIndex();
            if (nextPathIndex == ROOT_PATH_INDEX) {
                throw new InvalidStreamFileException(
                        "Block %d's StateProof has the root merkle path at index %d instead of the last index %d"
                                .formatted(blockNumber, index, rootIndex));
            }

            // Depth first order guarantees a path only ever contributes to a later path
            if (nextPathIndex <= index || nextPathIndex >= pathCount) {
                throw new InvalidStreamFileException(
                        "Block %d's StateProof has out of order next path index %d in merkle path %d"
                                .formatted(blockNumber, nextPathIndex, index));
            }

            final var parentChildren = children[nextPathIndex];
            if (parentChildren == null) {
                children[nextPathIndex] = new Children(pathHash, null);
            } else if (parentChildren.right() == null) {
                children[nextPathIndex] = new Children(parentChildren.left(), pathHash);
            } else {
                throw new InvalidStreamFileException("Block %d's StateProof has more than 2 children for merkle path %d"
                        .formatted(blockNumber, nextPathIndex));
            }
        }

        if (!foundRootHash) {
            throw new InvalidStreamFileException(
                    "Block %d's StateProof has no merkle path matching the block's root hash".formatted(blockNumber));
        }

        // The root path joins the two paths below it into the root hash the TSS signature is over
        final byte[] rootHash = joinChildren(blockNumber, digest, children[rootIndex], rootIndex);
        return foldSiblings(digest, lastPath, rootHash);
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

    private static byte[] joinChildren(
            final long blockNumber,
            final MessageDigest digest,
            final @Nullable Children pathChildren,
            final int index) {
        if (pathChildren == null) {
            throw new InvalidStreamFileException(
                    "Block %d's StateProof has no children in join point merkle path %d".formatted(blockNumber, index));
        }

        if (pathChildren.right() == null) {
            throw new InvalidStreamFileException(
                    "Block %d's StateProof has only one child in merkle path %d".formatted(blockNumber, index));
        }

        return HashUtils.hashInternalNode(digest, pathChildren.left(), pathChildren.right());
    }

    /**
     * The left and right leaves of a join point merkle path
     */
    private record Children(byte[] left, byte @Nullable [] right) {}
}
