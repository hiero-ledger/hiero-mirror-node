// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.record;

import com.hedera.hapi.block.stream.protoc.RecordFileItem;
import org.hiero.mirror.common.domain.transaction.RecordFile;

public interface RecordFileItemReader {

    /**
     * Reads the {@link RecordFileItem} object from a wrapped record block, and builds a {@link RecordFile} object
     *
     * @param recordFileItem - The {@link RecordFileItem} object
     * @param version - The record file version
     * @return The {@link RecordFile} object
     */
    RecordFile read(RecordFileItem recordFileItem, int version);
}
