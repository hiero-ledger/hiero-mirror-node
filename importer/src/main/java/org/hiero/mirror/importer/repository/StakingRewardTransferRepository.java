// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.transaction.StakingRewardTransfer;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface StakingRewardTransferRepository
        extends CrudRepository<StakingRewardTransfer, StakingRewardTransfer.Id>, RetentionRepository {

    @Modifying
    @Override
    @Query(
            value = "delete from staking_reward_transfer where consensus_timestamp <= :consensusTimestamp",
            nativeQuery = true)
    int prune(long consensusTimestamp);
}
