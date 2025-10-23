// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader;

import org.hiero.mirror.common.domain.StreamFile;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface StreamFileNotifier {

    void verified(StreamFile<?> streamFile);
}
