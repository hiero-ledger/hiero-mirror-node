// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.service;

import org.hiero.mirror.common.domain.entity.EntityId;

public interface FileDataService {

    /**
     * Retrieves the current file content in state. Note the state is a combination of data on disk and data in memory
     * from the transactions in a block which is being processed
     *
     * @param fileId The file's entity id
     * @return The file's content, {@code null} if not found or can't be served
     */
    byte[] get(EntityId fileId);
}
