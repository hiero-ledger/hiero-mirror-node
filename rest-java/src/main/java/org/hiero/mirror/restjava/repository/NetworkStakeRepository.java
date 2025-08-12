// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.Optional;
import org.hiero.mirror.common.domain.addressbook.NetworkStake;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface NetworkStakeRepository extends CrudRepository<NetworkStake, Long> {

    @Query(
            value =
                    """
        select *
        from network_stake
        where consensus_timestamp = (
            select max(consensus_timestamp) from network_stake
        )
        """,
            nativeQuery = true)
    Optional<NetworkStake> findLatest();
}
