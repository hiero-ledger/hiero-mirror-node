// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_BYTES;
import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_TIMESTAMP;

import java.util.List;
import java.util.Map;
import org.hiero.mirror.common.domain.clpr.ClprEndpoint;
import org.hiero.mirror.common.domain.clpr.ClprEndpointManifest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapperConfiguration.class, uses = ClprChannelMapper.class)
public interface ClprEndpointManifestMapper
        extends CollectionMapper<ClprEndpointManifest, org.hiero.mirror.rest.model.ClprEndpointManifest> {

    @Mapping(target = "consensusTimestamp", qualifiedByName = QUALIFIER_TIMESTAMP)
    @Mapping(target = "serviceAddress", qualifiedByName = QUALIFIER_BYTES)
    @Override
    org.hiero.mirror.rest.model.ClprEndpointManifest map(ClprEndpointManifest manifest);

    /** Reuses the channel mapper's endpoint conversion so both APIs render endpoints identically. */
    List<Map<String, Object>> mapEndpoints(List<ClprEndpoint> endpoints);
}
