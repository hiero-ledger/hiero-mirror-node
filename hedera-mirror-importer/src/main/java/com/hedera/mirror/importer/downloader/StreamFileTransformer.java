// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader;

import com.hedera.mirror.common.domain.StreamFile;

public interface StreamFileTransformer<T extends StreamFile<?>, S extends StreamFile<?>> {
    T transform(S s);
}
