// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import java.util.Optional;

interface EntityStakeRepositoryCustom {

    /**
     * Populates {@code entity_state_start} with the full period snapshot. Must be invoked once per end stake period,
     * within a caller-managed transaction, before {@link EntityStakeRepository#updateEntityStakeChunk}.
     */
    void createEntityStateStart(long stakingRewardAccount);

    /**
     * @return the ending period epoch day that should be processed next, if any
     */
    Optional<Long> getNextEndStakePeriod(long stakingRewardAccount);

    /**
     * @return the last entity id processed for the given end stake period (epoch day)
     */
    Optional<Long> getLastProcessedEntityId(long endStakePeriod);

    /**
     * Saves chunk progress for the given end stake period. Marking completed=true indicates the whole period finished.
     * Must be invoked within a caller-managed transaction when used as part of chunked pending reward calculation.
     */
    void saveProgress(long endStakePeriod, long lastEntityId, boolean completed);

    /**
     * @return the last entity id for the next chunk of entities to process
     */
    Optional<Long> getChunkUpperBoundEntityId(
            long stakingRewardAccount, long endStakePeriod, long startEntityIdExclusive, int chunkSize);
}
