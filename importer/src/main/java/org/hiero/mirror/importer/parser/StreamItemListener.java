// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser;

import org.hiero.mirror.common.domain.StreamItem;
import org.hiero.mirror.importer.exception.ImporterException;

public interface StreamItemListener<T extends StreamItem> {
    void onItem(T item) throws ImporterException;
}
