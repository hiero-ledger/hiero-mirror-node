// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import java.util.Optional;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.downloader.StreamFileNotifier;

public interface CutoverService extends StreamFileNotifier {

    Optional<RecordFile> getLastRecordFile();

    boolean shouldGetStream(StreamType streamType);
}
