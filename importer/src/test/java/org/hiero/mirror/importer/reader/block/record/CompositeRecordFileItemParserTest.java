// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.importer.reader.block.record.WrappedRecordBlockTestUtils.EXPECTED_RECORD_FILES;
import static org.hiero.mirror.importer.reader.block.record.WrappedRecordBlockTestUtils.readWrappedRecordBlocks;

import com.hedera.hapi.block.stream.protoc.RecordFileItem;
import java.util.stream.Stream;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class CompositeRecordFileItemParserTest {

    private static final RecursiveComparisonConfiguration RECORD_FILE_COMPARISON_CONFIG =
            RecursiveComparisonConfiguration.builder()
                    .withIgnoredFields("bytes", "items")
                    .build();

    private final RecordFileItemParser parser = new CompositeRecordFileItemParser();

    @Test
    void parseUnsupportedVersion() {
        assertThatThrownBy(() -> parser.parse(0, RecordFileItem.getDefaultInstance(), 0))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Unsupported record file version 0");
    }

    @ParameterizedTest(name = "block - {0}, version - {2}")
    @MethodSource("parseTestArgumentsProvider")
    void parse(final long blockNumber, final RecordFileItem recordFileItem, final int version) {
        final var recordFile = parser.parse(blockNumber, recordFileItem, version);
        assertThat(recordFile)
                .usingRecursiveComparison(RECORD_FILE_COMPARISON_CONFIG)
                .isEqualTo(EXPECTED_RECORD_FILES.get(blockNumber));
    }

    private static Stream<Arguments> parseTestArgumentsProvider() {
        return readWrappedRecordBlocks().stream().map(block -> {
            final var blockProof = block.getItems(3).getBlockProof();
            final long blockNumber = blockProof.getBlock();
            final int version = blockProof.getSignedRecordFileProof().getVersion();
            return Arguments.of(blockNumber, block.getItems(1).getRecordFile(), version);
        });
    }
}
