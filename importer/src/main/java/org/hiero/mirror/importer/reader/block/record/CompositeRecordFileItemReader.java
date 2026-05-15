// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.record;

import com.hedera.hapi.block.stream.protoc.RecordFileItem;
import jakarta.inject.Named;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.parser.record.sidecar.SidecarProperties;

@Named
public final class CompositeRecordFileItemReader implements RecordFileItemReader {

    private final RecordFileItemReaderV2 readerV2;
    private final RecordFileItemReaderV5 readerV5;
    private final RecordFileItemReaderV6 readerV6;

    public CompositeRecordFileItemReader(final SidecarProperties sidecarProperties) {
        this.readerV2 = new RecordFileItemReaderV2(sidecarProperties);
        this.readerV5 = new RecordFileItemReaderV5(sidecarProperties);
        this.readerV6 = new RecordFileItemReaderV6(sidecarProperties);
    }

    @Override
    public RecordFile read(final RecordFileItem recordFileItem, final int version) {
        final var reader =
                switch (version) {
                    case 2 -> readerV2;
                    case 5 -> readerV5;
                    case 6 -> readerV6;
                    default -> throw new UnsupportedOperationException("Unsupported record file version " + version);
                };

        return reader.read(recordFileItem, version);
    }
}
