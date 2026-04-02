// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.record;

import java.io.IOException;

final class RecordFileItemReaderV6 extends AbstractRecordFileItemReader {

    @Override
    protected void onEnd(final Context context) throws IOException {
        super.onEnd(context);
        context.dos().write(context.recordFileItem().getRecordFileContents().toByteArray());
    }
}
