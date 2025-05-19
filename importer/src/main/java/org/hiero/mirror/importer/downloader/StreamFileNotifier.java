// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader;

import jakarta.annotation.Nonnull;
import org.hiero.mirror.common.domain.StreamFile;

public interface StreamFileNotifier {

    void verified(@Nonnull StreamFile<?> streamFile);
}
