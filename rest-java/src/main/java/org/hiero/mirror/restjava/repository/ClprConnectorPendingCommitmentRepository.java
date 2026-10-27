// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.List;
import org.hiero.mirror.common.domain.clpr.ClprConnectorPendingCommitment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface ClprConnectorPendingCommitmentRepository
        extends CrudRepository<ClprConnectorPendingCommitment, byte[]> {

    /**
     * Returns the commitments that are still open, i.e. neither completed nor deleted. The predicate matches the
     * partial index {@code clpr_connector_pending_commitment__created_timestamp}.
     */
    @Query(value = """
            select * from clpr_connector_pending_commitment
            where deleted is false and completed_timestamp is null
            and created_timestamp >= :lowerBound
            and created_timestamp <= :upperBound
            """, nativeQuery = true)
    List<ClprConnectorPendingCommitment> findOpenByCreatedTimestampBetween(
            long lowerBound, long upperBound, Pageable pageable);
}
