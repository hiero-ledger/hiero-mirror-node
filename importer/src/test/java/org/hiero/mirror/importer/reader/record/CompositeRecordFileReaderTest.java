// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.record;

class CompositeRecordFileReaderTest extends RecordFileReaderTest {

    @Override
    protected RecordFileReader getRecordFileReader() {
        RecordFileReaderImplV1 v1Reader = new RecordFileReaderImplV1();
        RecordFileReaderImplV2 v2Reader = new RecordFileReaderImplV2();
        RecordFileReaderImplV5 v5Reader = new RecordFileReaderImplV5();
        return new CompositeRecordFileReader(v1Reader, v2Reader, v5Reader, new ProtoRecordFileReader());
    }

    @Override
    protected boolean filterFile(int version) {
        return true;
    }
}
