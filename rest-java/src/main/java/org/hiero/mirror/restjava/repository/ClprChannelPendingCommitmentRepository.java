// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.List;
import org.hiero.mirror.common.domain.clpr.ClprChannelPendingCommitment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface ClprChannelPendingCommitmentRepository extends CrudRepository<ClprChannelPendingCommitment, byte[]> {

    /**
     * Returns the commitments that are still open, i.e. neither completed nor deleted. The predicate matches the
     * partial index {@code clpr_channel_pending_commitment__created_timestamp}.
     */
    @Query(value = """
            select * from clpr_channel_pending_commitment
            where deleted is false and completed_timestamp is null
            and created_timestamp >= :lowerBound
            and created_timestamp <= :upperBound
            """, nativeQuery = true)
    List<ClprChannelPendingCommitment> findOpenByCreatedTimestampBetween(
            long lowerBound, long upperBound, Pageable pageable);
}
