// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_TIMESTAMP;

import org.hiero.mirror.common.domain.clpr.ClprChannelPendingCommitment;
import org.hiero.mirror.rest.model.ClprPendingChannel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapperConfiguration.class)
public interface ClprPendingChannelMapper extends CollectionMapper<ClprChannelPendingCommitment, ClprPendingChannel> {

    @Mapping(source = "createdTimestamp", target = "createdTimestamp", qualifiedByName = QUALIFIER_TIMESTAMP)
    // Only open commitments are exposed, see ClprChannelPendingCommitmentRepository
    @Mapping(target = "status", constant = "OPEN")
    ClprPendingChannel map(ClprChannelPendingCommitment source);
}
