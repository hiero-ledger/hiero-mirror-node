// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.List;
import org.hiero.mirror.common.domain.clpr.ClprLedgerConfiguration;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface ClprLedgerConfigurationRepository extends CrudRepository<ClprLedgerConfiguration, Long> {

    @Query(value = """
            select * from clpr_ledger_configuration
            where consensus_timestamp >= :lowerBound
            and consensus_timestamp <= :upperBound
            """, nativeQuery = true)
    List<ClprLedgerConfiguration> findByConsensusTimestampBetween(long lowerBound, long upperBound, Pageable pageable);
}
