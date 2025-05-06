// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.springframework.transaction.annotation.Transactional;

/**
 * Repositories implementing this interface support the concept of a retention period that prunes historical data.
 */
public interface RetentionRepository {

    @Transactional
    int prune(long consensusTimestamp);
}
