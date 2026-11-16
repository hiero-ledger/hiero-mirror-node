// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.clpr.ClprChannelPendingCommitmentHistory;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface ClprChannelPendingCommitmentHistoryRepository
        extends CrudRepository<ClprChannelPendingCommitmentHistory, byte[]>, RetentionRepository {

    @Modifying
    @Override
    @Query(
            nativeQuery = true,
            value = "delete from clpr_channel_pending_commitment_history where timestamp_range << int8range(?1, null)")
    int prune(long consensusTimestamp);
}
