// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader;

import org.hiero.mirror.common.domain.StreamFile;

public interface StreamFileTransformer<T extends StreamFile<?>, S extends StreamFile<?>> {
    T transform(S s);
}
