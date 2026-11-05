// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_BYTES;
import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_TIMESTAMP;

import org.hiero.mirror.common.domain.clpr.ClprChannelPendingCommitment;
import org.hiero.mirror.rest.model.ClprChannel;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps a pending channel commitment to the channel API model, where it only has {@code created_timestamp},
 * {@code ownership_commitment}, and a {@code PENDING} status.
 */
@Mapper(config = MapperConfiguration.class)
public interface ClprPendingChannelMapper extends CollectionMapper<ClprChannelPendingCommitment, ClprChannel> {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "createdTimestamp", qualifiedByName = QUALIFIER_TIMESTAMP)
    @Mapping(target = "ownershipCommitment", qualifiedByName = QUALIFIER_BYTES)
    @Mapping(target = "status", constant = "PENDING")
    @Override
    ClprChannel map(ClprChannelPendingCommitment pending);
}
