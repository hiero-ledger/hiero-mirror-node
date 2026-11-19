// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import org.hiero.mirror.common.domain.clpr.ClprEndpointManifest;
import org.jspecify.annotations.Nullable;
import org.springframework.data.repository.Repository;

public interface ClprEndpointManifestRepository extends Repository<ClprEndpointManifest, Long> {

    /** Returns the latest manifest whose consensus timestamp is within the inclusive bounds, or null if none. */
    @Nullable
    ClprEndpointManifest findFirstByConsensusTimestampBetweenOrderByConsensusTimestampDesc(
            long lowerBound, long upperBound);
}
