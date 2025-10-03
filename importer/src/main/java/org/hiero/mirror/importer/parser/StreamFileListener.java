// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser;

import org.hiero.mirror.common.domain.StreamFile;
import org.hiero.mirror.importer.exception.ImporterException;

public interface StreamFileListener<T extends StreamFile<?>> {

    default void onEnd(T streamFile) throws ImporterException {}

    default void onStart(T streamFile) {}
}
