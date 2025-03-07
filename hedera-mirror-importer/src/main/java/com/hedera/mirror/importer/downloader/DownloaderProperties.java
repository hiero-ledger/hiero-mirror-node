// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader;

import com.hedera.mirror.common.domain.StreamType;
import java.time.Duration;

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
