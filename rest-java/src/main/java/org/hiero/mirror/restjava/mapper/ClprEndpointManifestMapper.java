// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_HEX;
import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_TIMESTAMP;

import org.hiero.mirror.common.domain.clpr.ClprEndpointManifest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapperConfiguration.class)
public interface ClprEndpointManifestMapper
        extends CollectionMapper<ClprEndpointManifest, org.hiero.mirror.rest.model.ClprEndpointManifestEntry> {

    @Mapping(source = "consensusTimestamp", target = "consensusTimestamp", qualifiedByName = QUALIFIER_TIMESTAMP)
    @Mapping(source = "serviceAddress", target = "serviceAddress", qualifiedByName = QUALIFIER_HEX)
    org.hiero.mirror.rest.model.ClprEndpointManifestEntry map(ClprEndpointManifest source);
}
