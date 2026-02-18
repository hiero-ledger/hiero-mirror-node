// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import static org.apache.commons.lang3.StringUtils.leftPad;

import com.hedera.hapi.block.stream.output.protoc.BlockHeader;
import com.hedera.hapi.block.stream.protoc.BlockProof;
import com.hedera.hapi.block.stream.protoc.RecordFileItem;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Singular;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.domain.DigestAlgorithm;
import org.hiero.mirror.common.domain.StreamFile;
import org.hiero.mirror.common.domain.StreamType;
import org.jspecify.annotations.NonNull;

@Builder(toBuilder = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public final class BlockFile implements StreamFile<BlockTransaction> {

    private static final int BASENAME_LENGTH = 19;
    private static final char BASENAME_PADDING = '0';
    private static final String COMPRESSED_FILE_SUFFIX = ".blk.zstd";
    private static final String FILE_SUFFIX = ".blk";
    private static final Predicate<String> STREAMED_FILENAME_PREDICATE =
            Pattern.compile("^\\d{19}.blk$").asPredicate();

    @ToString.Exclude
    private BlockHeader blockHeader;

    @ToString.Exclude
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
    private List<BlockTransaction> items;

    @ToString.Exclude
    private BlockTransaction lastLedgerIdPublicationTransaction;

    private Long loadEnd;

    private Long loadStart;

    private String name;

    private String node;

    @ToString.Exclude
    private String previousHash;

    private byte[] rawHash;

    private RecordFileItem recordFileItem;

    private Long roundEnd;

    private Long roundStart;

    private Integer size;

    private int version;

    public static String getFilename(final long blockNumber, final boolean compressed) {
        if (blockNumber < 0) {
            throw new IllegalArgumentException("Block number must be non-negative");
        }

        var filename = leftPad(Long.toString(blockNumber), BASENAME_LENGTH, BASENAME_PADDING);
        return compressed ? filename + COMPRESSED_FILE_SUFFIX : filename + FILE_SUFFIX;
    }

    @Override
    public StreamFile<BlockTransaction> copy() {
        return this.toBuilder().build();
    }

    @Override
    public String getFileHash() {
        return StringUtils.EMPTY;
    }

    public Long getNodeId() {
        return -1L;
    }

    public BlockSourceType getSourceType() {
        if (StringUtils.isBlank(name)) {
            return null;
        }

        if (STREAMED_FILENAME_PREDICATE.test(name)) {
            return BlockSourceType.BLOCK_NODE;
        }

        return BlockSourceType.FILE;
    }

    @Override
    public StreamType getType() {
        return StreamType.BLOCK;
    }

    @Override
    public void setNodeId(@NonNull Long nodeId) {
        // ignored
    }

    public static class BlockFileBuilder {

        public BlockFileBuilder onNewRound(final long roundNumber) {
            if (roundStart == null) {
                roundStart = roundNumber;
            }

            roundEnd = roundNumber;
            return this;
        }
    }
}
