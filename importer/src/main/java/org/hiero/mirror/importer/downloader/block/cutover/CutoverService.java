// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.cutover;

import java.util.Optional;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.downloader.StreamFileNotifier;

public interface CutoverService extends StreamFileNotifier {

    /**
     * Given the recordstream to blockstream cutover configuration, conditionally run the {@code task} to get
     * {@code streamType} stream.
     *
     * @param streamType The stream type
     * @param task The task to get the stream
     */
    void get(StreamType streamType, Runnable task);

    Optional<RecordFile> getLastRecordFile();
}
