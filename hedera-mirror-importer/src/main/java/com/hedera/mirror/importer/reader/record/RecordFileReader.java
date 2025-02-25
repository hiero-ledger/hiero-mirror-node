// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.reader.record;

import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.importer.reader.StreamFileReader;

public interface RecordFileReader extends StreamFileReader<RecordFile, RecordItem> {

    int MAX_TRANSACTION_LENGTH = 64 * 1024;
}
