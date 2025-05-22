// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.pubsub;

import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.exception.ImporterException;
import org.hiero.mirror.importer.parser.record.RecordStreamFileListener;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.hiero.mirror.importer.repository.SidecarFileRepository;

@Named
@RequiredArgsConstructor
@ConditionalOnPubSubRecordParser
public class PubSubRecordStreamFileListener implements RecordStreamFileListener {

    private final RecordFileRepository recordFileRepository;
    private final SidecarFileRepository sidecarFileRepository;

    @Override
    public void onEnd(RecordFile recordFile) throws ImporterException {
        if (recordFile != null) {
            recordFileRepository.save(recordFile);
            sidecarFileRepository.saveAll(recordFile.getSidecars());
        }
    }
}
