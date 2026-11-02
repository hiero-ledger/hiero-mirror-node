// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_TIMESTAMP;

import org.hiero.mirror.common.domain.clpr.ClprChannelPendingCommitment;
import org.hiero.mirror.rest.model.ClprChannel;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps an open pending commitment to a channel whose id and state aren't known until the commitment is revealed. */
@Mapper(config = MapperConfiguration.class)
public interface ClprPendingChannelMapper extends CollectionMapper<ClprChannelPendingCommitment, ClprChannel> {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(source = "createdTimestamp", target = "createdTimestamp", qualifiedByName = QUALIFIER_TIMESTAMP)
    @Mapping(source = "ownershipCommitment", target = "ownershipCommitment")
    // Only open commitments are exposed, see ClprChannelPendingCommitmentRepository
    @Mapping(target = "status", constant = "PENDING")
    ClprChannel map(ClprChannelPendingCommitment source);
}
