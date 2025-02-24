// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser;

import com.hedera.mirror.common.domain.StreamFile;
import com.hedera.mirror.importer.exception.ImporterException;

public interface StreamFileListener<T extends StreamFile<?>> {

    void onEnd(T streamFile) throws ImporterException;
}
