// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.record;

import com.hedera.hapi.block.stream.protoc.RecordFileItem;
import org.hiero.mirror.common.domain.transaction.RecordFile;

public interface RecordFileItemParser {

    /**
     * Parses the {@link RecordFileItem} object from a wrapped record block, and builds a {@link RecordFile} object
     *
     * @param blockNumber - The block number
     * @param recordFileItem - The {@link RecordFileItem} object
     * @param version - The record file version
     * @return The {@link RecordFile} object
     */
    RecordFile parse(long blockNumber, RecordFileItem recordFileItem, int version);
}
