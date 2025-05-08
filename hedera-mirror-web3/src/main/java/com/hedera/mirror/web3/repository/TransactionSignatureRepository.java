// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.repository;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.TransactionSignature;
import java.util.List;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionSignatureRepository extends CrudRepository<TransactionSignature, TransactionSignature.Id> {
    List<TransactionSignature> findByEntityId(EntityId entityId);

    List<TransactionSignature> findByEntityIdAndConsensusTimestampLessThanEqual(
            EntityId entityId, long consensusTimestamp);
}
