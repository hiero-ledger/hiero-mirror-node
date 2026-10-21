// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_TIMESTAMP;

import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.rest.model.ClprSignatureScheme;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ValueMapping;

@Mapper(config = MapperConfiguration.class)
public interface ClprConnectorMapper
        extends CollectionMapper<ClprConnector, org.hiero.mirror.rest.model.ClprConnector> {

    @Mapping(source = "createdTimestamp", target = "createdTimestamp", qualifiedByName = QUALIFIER_TIMESTAMP)
    // Without an explicit mapping, MapStruct would pick CommonMapper.mapEntityId(Long) for Long to String
    @Mapping(
            target = "lockedStake",
            expression = "java(source.getLockedStake() == null ? null : source.getLockedStake().toString())")
    @Mapping(source = "timestampRange", target = "timestamp")
    org.hiero.mirror.rest.model.ClprConnector map(ClprConnector source);

    @ValueMapping(source = "ECDSA_SECP256K1", target = "ECDSA_SECP256_K1")
    ClprSignatureScheme mapSignatureScheme(org.hiero.mirror.common.domain.clpr.ClprSignatureScheme source);
}
