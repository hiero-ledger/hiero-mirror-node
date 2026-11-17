// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.clpr.ClprChannelPendingCommitmentHistory;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface ClprChannelPendingCommitmentHistoryRepository
        extends CrudRepository<ClprChannelPendingCommitmentHistory, byte[]>, RetentionRepository {

    @Modifying
    @Override
    @Query("delete from clpr_channel_pending_commitment_history where timestamp_range << int8range(:consensusTimestamp, null)")
    int prune(long consensusTimestamp);
}
