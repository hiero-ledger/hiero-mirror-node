// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader;

import java.time.Duration;
import org.hiero.mirror.common.domain.StreamType;

public interface DownloaderProperties {

    CommonDownloaderProperties getCommon();

    Duration getFrequency();

    StreamType getStreamType();

    boolean isEnabled();

    void setEnabled(boolean enabled);

    boolean isPersistBytes();

    void setPersistBytes(boolean keepBytes);

    boolean isWriteFiles();

    void setWriteFiles(boolean keepFiles);

    boolean isWriteSignatures();

    void setWriteSignatures(boolean keepSignatures);
}
