// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.List;
import org.hiero.mirror.common.domain.clpr.ClprEndpointManifest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface ClprEndpointManifestRepository extends CrudRepository<ClprEndpointManifest, Long> {

    @Query(value = """
            select * from clpr_endpoint_manifest
            where consensus_timestamp >= :lowerBound
            and consensus_timestamp <= :upperBound
            """, nativeQuery = true)
    List<ClprEndpointManifest> findByConsensusTimestampBetween(long lowerBound, long upperBound, Pageable pageable);
}
