// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.service;

import org.hiero.mirror.common.domain.entity.EntityId;

public interface FileDataService {

    /**
     * Retrieves the file content at the given consensus timestamp.
     *
     * <p>The service maintains its file data state in sync with the last processed HFS transaction. Therefore, this
     * method returns {@code null} if the requested timestamp is earlier than the consensus timestamp of the last
     * processed HFS transaction.
     *
     * @param consensusTimestamp The consensus timestamp at which to get the file's content
     * @param fileId The file's entity id
     * @return The file's content, {@code null} if not found or can't be served
     */
    byte[] get(long consensusTimestamp, EntityId fileId);
}
