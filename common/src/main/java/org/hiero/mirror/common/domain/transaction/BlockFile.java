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

@Builder(toBuilder = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BlockFile implements StreamFile<BlockItem> {

    private static final int BASENAME_LENGTH = 36;
    private static final char BASENAME_PADDING = '0';
    private static final String COMPRESSED_FILE_SUFFIX = ".blk.gz";
    private static final String FILE_SUFFIX = ".blk";
    private static final Predicate<String> STREAMED_FILENAME_PREDICATE =
            Pattern.compile("^\\d{36}.blk$").asPredicate();

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

    public static String getFilename(long blockNumber, boolean gzipped) {
        if (blockNumber < 0) {
            throw new IllegalArgumentException("Block number must be non-negative");
        }

        var filename = leftPad(Long.toString(blockNumber), BASENAME_LENGTH, BASENAME_PADDING);
        return gzipped ? filename + COMPRESSED_FILE_SUFFIX : filename + FILE_SUFFIX;
    }

    @Override
    public StreamFile<BlockItem> copy() {
        return this.toBuilder().build();
    }

    @Override
    public String getFileHash() {
        return StringUtils.EMPTY;
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

    public static class BlockFileBuilder {

        public BlockFileBuilder onNewRound(long roundNumber) {
            if (roundStart == null) {
                roundStart = roundNumber;
            }

            roundEnd = roundNumber;
            return this;
        }
    }
}
