// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.record;

import java.io.IOException;
import org.hiero.mirror.importer.parser.record.sidecar.SidecarProperties;

final class RecordFileItemReaderV6 extends AbstractRecordFileItemReader {

    RecordFileItemReaderV6(final SidecarProperties sidecarProperties) {
        super(sidecarProperties);
    }

    @Override
    protected void onEnd(final Context context) throws IOException {
        super.onEnd(context);
        context.dos().write(context.recordFileItem().getRecordFileContents().toByteArray());
    }
}
