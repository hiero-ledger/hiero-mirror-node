// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.record;

class RecordFileReaderImplV2Test extends AbstractRecordFileReaderTest {

    @Override
    protected RecordFileReader getRecordFileReader() {
        return new RecordFileReaderImplV2();
    }

    @Override
    protected boolean filterFile(int version) {
        return version == 2;
    }
}
