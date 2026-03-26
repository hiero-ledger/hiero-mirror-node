// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.record;

import java.io.IOException;

final class RecordFileItemParserV6 extends AbstractRecordFileItemParser {

    @Override
    protected void onEnd(final Context context) throws IOException {
        super.onEnd(context);
        context.dos().write(context.recordFileItem().getRecordFileContents().toByteArray());
    }
}
