// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_BYTES;
import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_TIMESTAMP;

import org.hiero.mirror.common.domain.clpr.ClprEndpointManifest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Reuses {@link ClprChannelMapper}'s endpoint conversion so both APIs render endpoints identically. */
@Mapper(config = MapperConfiguration.class, uses = ClprChannelMapper.class)
public interface ClprEndpointManifestMapper
        extends CollectionMapper<ClprEndpointManifest, org.hiero.mirror.rest.model.ClprEndpointManifest> {

    @Mapping(target = "consensusTimestamp", qualifiedByName = QUALIFIER_TIMESTAMP)
    @Mapping(target = "serviceAddress", qualifiedByName = QUALIFIER_BYTES)
    @Override
    org.hiero.mirror.rest.model.ClprEndpointManifest map(ClprEndpointManifest manifest);
}
