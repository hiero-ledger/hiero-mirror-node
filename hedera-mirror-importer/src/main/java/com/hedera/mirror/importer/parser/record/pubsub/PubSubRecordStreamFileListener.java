// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.record.pubsub;

import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.parser.record.RecordStreamFileListener;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.SidecarFileRepository;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;

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
