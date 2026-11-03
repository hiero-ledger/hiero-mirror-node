// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.List;
import org.hiero.mirror.common.domain.clpr.ClprChannelPendingCommitment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

/**
 * Backs the Channels API's {@code status=eq:PENDING}: channel commitments registered but not yet completed or
 * abandoned. {@code created_timestamp} is the sole sort key. The table isn't distributed in {@code v2}. All bounds are
 * inclusive and computed by the service.
 */
public interface ClprPendingChannelRepository extends CrudRepository<ClprChannelPendingCommitment, byte[]> {

    /**
     * The {@code 'OPEN'} literal is deliberate: a bind parameter may not match the predicate of the partial index
     * {@code clpr_channel_pending_commitment__created_timestamp}, which is defined {@code where status = 'OPEN'}.
     */
    @Query(value = """
            select * from clpr_channel_pending_commitment
            where status = 'OPEN'
            and created_timestamp >= :lowerBound
            and created_timestamp <= :upperBound
            """, nativeQuery = true)
    List<ClprChannelPendingCommitment> findOpenByCreatedTimestampBetween(
            long lowerBound, long upperBound, Pageable pageable);
}
