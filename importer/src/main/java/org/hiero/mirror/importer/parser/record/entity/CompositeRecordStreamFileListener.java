// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity;

import jakarta.inject.Named;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.exception.ImporterException;
import org.hiero.mirror.importer.parser.record.RecordStreamFileListener;
import org.springframework.context.annotation.Primary;

@Named
@Primary
@RequiredArgsConstructor
public class CompositeRecordStreamFileListener implements RecordStreamFileListener {

    private final List<RecordStreamFileListener> listeners;

    @Override
    public void onEnd(RecordFile streamFile) throws ImporterException {
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).onEnd(streamFile);
        }
    }
}
