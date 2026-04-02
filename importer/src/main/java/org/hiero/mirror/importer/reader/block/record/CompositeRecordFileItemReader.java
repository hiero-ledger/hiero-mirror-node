// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.record;

import com.hedera.hapi.block.stream.protoc.RecordFileItem;
import jakarta.inject.Named;
import org.hiero.mirror.common.domain.transaction.RecordFile;

@Named
public final class CompositeRecordFileItemReader implements RecordFileItemReader {

    private static final RecordFileItemReaderV2 READER_V2 = new RecordFileItemReaderV2();
    private static final RecordFileItemReaderV5 READER_V5 = new RecordFileItemReaderV5();
    private static final RecordFileItemReaderV6 READER_V6 = new RecordFileItemReaderV6();

    @Override
    public RecordFile read(final RecordFileItem recordFileItem, final int version) {
        final var reader =
                switch (version) {
                    case 2 -> READER_V2;
                    case 5 -> READER_V5;
                    case 6 -> READER_V6;
                    default -> throw new UnsupportedOperationException("Unsupported record file version " + version);
                };

        return reader.read(recordFileItem, version);
    }
}
