// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.hash;

import com.hedera.hapi.block.stream.protoc.MerklePath;
import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface BlockStateProofHasher {

    /**
     * Gets the root hash of the block the TSS signature is for, given the merkle paths.
     *
     * @param blockNumber - The block number of the block with the indirect StateProof
     * @param currentRootHash - The root hash of the current block
     * @param merklePaths - The merkle paths in a StateProof
     * @return The root hash calculated by following the merkle paths
     */
    byte[] getRootHash(long blockNumber, final byte[] currentRootHash, List<MerklePath> merklePaths);
}
