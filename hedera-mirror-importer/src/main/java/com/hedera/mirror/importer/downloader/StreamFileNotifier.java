// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader;

import com.hedera.mirror.common.domain.StreamFile;
import jakarta.annotation.Nonnull;

public interface StreamFileNotifier {

    void verified(@Nonnull StreamFile<?> streamFile);
}
