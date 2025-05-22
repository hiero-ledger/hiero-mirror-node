// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.record;

import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.importer.reader.StreamFileReader;

public interface RecordFileReader extends StreamFileReader<RecordFile, RecordItem> {

    int MAX_TRANSACTION_LENGTH = 64 * 1024;
}
