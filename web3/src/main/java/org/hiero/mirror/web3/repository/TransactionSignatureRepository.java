// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import java.util.List;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.TransactionSignature;
import org.springframework.data.repository.CrudRepository;

public interface TransactionSignatureRepository extends CrudRepository<TransactionSignature, TransactionSignature.Id> {
    List<TransactionSignature> findByEntityId(EntityId entityId);

    /**
     * Retrieves transaction signatures for a specific entity ID, filtering by a consensus timestamp.
     * This method returns all signatures for the given entity that were valid at or before the provided consensus timestamp.
     *
     * @param entityId           the ID of the entity to fetch transaction signatures for.
     * @param consensusTimestamp the consensus timestamp to filter the signatures by.
     * @return a list of `TransactionSignature` entities for the specified entity ID,
     *         filtered by the consensus timestamp.
     */
    List<TransactionSignature> findByEntityIdAndConsensusTimestampLessThanEqual(
            EntityId entityId, long consensusTimestamp);
}
