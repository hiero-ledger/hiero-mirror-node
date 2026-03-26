// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.record;

import com.hedera.hapi.block.stream.protoc.RecordFileItem;
import org.hiero.mirror.common.domain.transaction.RecordFile;

public final class CompositeRecordFileItemParser implements RecordFileItemParser {

    private static final RecordFileItemParserV2 PARSER_V2 = new RecordFileItemParserV2();
    private static final RecordFileItemParserV5 PARSER_V5 = new RecordFileItemParserV5();
    private static final RecordFileItemParserV6 PARSER_V6 = new RecordFileItemParserV6();

    @Override
    public RecordFile parse(final long blockNumber, final RecordFileItem recordFileItem, final int version) {
        final var parser =
                switch (version) {
                    case 2 -> PARSER_V2;
                    case 5 -> PARSER_V5;
                    case 6 -> PARSER_V6;
                    default -> throw new UnsupportedOperationException("Unsupported record file version " + version);
                };

        return parser.parse(blockNumber, recordFileItem, version);
    }
}
