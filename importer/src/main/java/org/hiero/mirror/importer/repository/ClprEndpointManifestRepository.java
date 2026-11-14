// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.clpr.ClprEndpointManifest;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface ClprEndpointManifestRepository extends CrudRepository<ClprEndpointManifest, Long> {

    @Query(nativeQuery = true, value = "select * from clpr_endpoint_manifest order by consensus_timestamp desc limit 1")
    @Nullable
    ClprEndpointManifest findLatest();
}
