// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.common.domain.transaction;

import static org.apache.commons.lang3.StringUtils.leftPad;

import com.hedera.hapi.block.stream.output.protoc.BlockHeader;
import com.hedera.hapi.block.stream.protoc.BlockProof;
import com.hedera.hapi.block.stream.protoc.RecordFileItem;
import com.hedera.mirror.common.domain.DigestAlgorithm;
import com.hedera.mirror.common.domain.StreamFile;
import com.hedera.mirror.common.domain.StreamType;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Singular;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

@Builder(toBuilder = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BlockFile implements StreamFile<BlockItem> {

    private static final int BASENAME_LENGTH = 36;
    private static final char BASENAME_PADDING = '0';
    private static final String COMPRESSED_FILE_SUFFIX = ".blk.gz";

    private BlockHeader blockHeader;

    private BlockProof blockProof;

    @ToString.Exclude
    private byte[] bytes;

    private Long consensusStart;

    private Long consensusEnd;

    private Long count;

    private DigestAlgorithm digestAlgorithm;

    @ToString.Exclude
    private String hash;

    private Long index;

    @EqualsAndHashCode.Exclude
    @Singular
    @ToString.Exclude
    private List<BlockItem> items;

    private Long loadEnd;

    private Long loadStart;

    private String name;

    private Long nodeId;

    @ToString.Exclude
    private String previousHash;

    private RecordFileItem recordFileItem;

    private Long roundEnd;

    private Long roundStart;

    private Integer size;

    private int version;

    public static String getBlockStreamFilename(long blockNumber) {
        if (blockNumber < 0) {
            throw new IllegalArgumentException("Block number must be non-negative");
        }

        return leftPad(Long.toString(blockNumber), BASENAME_LENGTH, BASENAME_PADDING) + COMPRESSED_FILE_SUFFIX;
    }

    @Override
    public StreamFile<BlockItem> copy() {
        return this.toBuilder().build();
    }

    @Override
    public String getFileHash() {
        return StringUtils.EMPTY;
    }

    @Override
    public StreamType getType() {
        return StreamType.BLOCK;
    }

    public static class BlockFileBuilder {

        public BlockFileBuilder onNewRound(long roundNumber) {
            if (roundStart == null) {
                roundStart = roundNumber;
            }

            roundEnd = roundNumber;
            return this;
        }

        public BlockFileBuilder onNewTransaction(long consensusTimestamp) {
            if (consensusStart == null) {
                consensusStart = consensusTimestamp;
            }

            consensusEnd = consensusTimestamp;
            return this;
        }
    }
}
