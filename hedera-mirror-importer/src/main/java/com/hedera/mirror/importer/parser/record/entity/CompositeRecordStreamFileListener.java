// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.record.entity;

import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.parser.record.RecordStreamFileListener;
import jakarta.inject.Named;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
