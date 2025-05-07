// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.record;

import jakarta.inject.Named;
import java.io.InputStream;

@Named
public class RecordFileReaderImplV1 extends AbstractPreV5RecordFileReader {

    public RecordFileReaderImplV1() {
        super(1);
    }

    @Override
    protected RecordFileDigest getRecordFileDigest(final InputStream is) {
        return new RecordFileDigest(is, true);
    }
}
