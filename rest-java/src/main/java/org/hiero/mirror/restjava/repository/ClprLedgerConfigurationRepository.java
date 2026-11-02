// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.Optional;
import org.hiero.mirror.common.domain.clpr.ClprLedgerConfiguration;
import org.springframework.data.repository.CrudRepository;

public interface ClprLedgerConfigurationRepository extends CrudRepository<ClprLedgerConfiguration, Long> {

    Optional<ClprLedgerConfiguration> findFirstByConsensusTimestampLessThanEqualOrderByConsensusTimestampDesc(
            long consensusTimestamp);
}
