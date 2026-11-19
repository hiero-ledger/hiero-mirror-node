// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import org.hiero.mirror.common.domain.clpr.ClprLedgerConfiguration;
import org.jspecify.annotations.Nullable;
import org.springframework.data.repository.Repository;

public interface ClprLedgerConfigurationRepository extends Repository<ClprLedgerConfiguration, Long> {

    /** Returns the latest configuration whose consensus timestamp is within the inclusive bounds, or null if none. */
    @Nullable
    ClprLedgerConfiguration findFirstByConsensusTimestampBetweenOrderByConsensusTimestampDesc(
            long lowerBound, long upperBound);
}
