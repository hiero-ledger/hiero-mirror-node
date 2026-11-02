// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.Optional;
import org.hiero.mirror.common.domain.clpr.ClprEndpointManifest;
import org.springframework.data.repository.CrudRepository;

public interface ClprEndpointManifestRepository extends CrudRepository<ClprEndpointManifest, Long> {

    Optional<ClprEndpointManifest> findFirstByConsensusTimestampLessThanEqualOrderByConsensusTimestampDesc(
            long consensusTimestamp);
}
