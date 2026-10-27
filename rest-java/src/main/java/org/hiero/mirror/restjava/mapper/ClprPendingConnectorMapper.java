// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_TIMESTAMP;

import org.hiero.mirror.common.domain.clpr.ClprConnectorPendingCommitment;
import org.hiero.mirror.rest.model.ClprPendingConnector;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapperConfiguration.class)
public interface ClprPendingConnectorMapper
        extends CollectionMapper<ClprConnectorPendingCommitment, ClprPendingConnector> {

    @Mapping(source = "createdTimestamp", target = "createdTimestamp", qualifiedByName = QUALIFIER_TIMESTAMP)
    // Only open commitments are exposed, see ClprConnectorPendingCommitmentRepository
    @Mapping(target = "status", constant = "OPEN")
    ClprPendingConnector map(ClprConnectorPendingCommitment source);
}
