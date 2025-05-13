// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.record;

import jakarta.inject.Named;
import java.io.InputStream;

@Named
public class RecordFileReaderImplV2 extends AbstractPreV5RecordFileReader {

    public RecordFileReaderImplV2() {
        super(2);
    }

    @Override
    protected RecordFileDigest getRecordFileDigest(InputStream is) {
        return new RecordFileDigest(is, false);
    }
}
