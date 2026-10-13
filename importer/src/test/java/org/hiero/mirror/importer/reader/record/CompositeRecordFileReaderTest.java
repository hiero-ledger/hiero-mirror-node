// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.record;

import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;

class CompositeRecordFileReaderTest extends RecordFileReaderTest {

    @Override
    protected RecordFileReader getRecordFileReader() {
        RecordFileReaderImplV1 v1Reader = new RecordFileReaderImplV1();
        RecordFileReaderImplV2 v2Reader = new RecordFileReaderImplV2();
        RecordFileReaderImplV5 v5Reader = new RecordFileReaderImplV5();
        final var properties = new CommonDownloaderProperties(new ImporterProperties());
        return new CompositeRecordFileReader(v1Reader, v2Reader, v5Reader, new ProtoRecordFileReader(properties));
    }

    @Override
    protected boolean filterFile(int version) {
        return true;
    }
}
