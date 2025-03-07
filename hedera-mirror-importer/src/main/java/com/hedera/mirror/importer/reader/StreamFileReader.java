// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.reader;

import com.hedera.mirror.common.domain.StreamFile;
import com.hedera.mirror.common.domain.StreamItem;
import com.hedera.mirror.importer.domain.StreamFileData;

public interface StreamFileReader<S extends StreamFile<I>, I extends StreamItem> {

    /**
     * Reads a stream file. This method takes ownership of the {@link java.io.InputStream} provided by {@code
     * streamFileData} and will close it when it's done processing the data. Depending upon the implementation, the
     * StreamFile::getItems may return a lazily parsed list of items.
     *
     * @param streamFileData {@link StreamFileData} object for the record file.
     * @return {@link StreamFile} object
     */
    S read(StreamFileData streamFileData);
}
