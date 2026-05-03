// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import java.util.Optional;
import org.hiero.mirror.common.domain.token.CustomFee;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface CustomFeeRepository extends CrudRepository<CustomFee, Long> {

    /**
     * Retrieves custom fee by its entityId up to a given block timestamp.
     * The method considers both the current state of the custom fee and its historical states
     * and returns the latest valid just before or equal to the provided block timestamp.
     *
     * @param entityId the entity ID with custom fees
     * @param blockTimestamp  the block timestamp used to filter the results.
     * @return an Optional containing the custom fee state at the specified timestamp.
     * If there is no record found for the given criteria, an empty Optional is returned.
     */
    @Query(value = """
            (
                select *
                from custom_fee
                where entity_id = :entityId
                    and lower(timestamp_range) <= :blockTimestamp
            )
            union all
            (
                select *
                from custom_fee_history
                where entity_id = :entityId
                    and lower(timestamp_range) <= :blockTimestamp
                order by lower(timestamp_range) desc
                limit 1
            )
            order by timestamp_range desc
            limit 1
            """, nativeQuery = true)
    Optional<CustomFee> findByTokenIdAndTimestamp(long entityId, long blockTimestamp);
}
