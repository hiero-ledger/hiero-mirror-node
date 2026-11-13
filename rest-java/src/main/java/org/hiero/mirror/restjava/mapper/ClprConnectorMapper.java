// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_BYTES;
import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_TIMESTAMP;

import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprSignatureScheme;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ValueMapping;

/**
 * Maps connectors to the API model. A pending connector only has the registration's {@code channel_id},
 * {@code commitment} and {@code created_timestamp}, so the rest is null or the column defaults.
 */
@Mapper(config = MapperConfiguration.class)
public interface ClprConnectorMapper
        extends CollectionMapper<ClprConnector, org.hiero.mirror.rest.model.ClprConnector> {

    String QUALIFIER_LOCKED_STAKE = "lockedStake";

    @Mapping(target = "channelId", qualifiedByName = QUALIFIER_BYTES)
    @Mapping(target = "commitment", qualifiedByName = QUALIFIER_BYTES)
    @Mapping(target = "connectorId", qualifiedByName = QUALIFIER_BYTES)
    @Mapping(target = "createdTimestamp", qualifiedByName = QUALIFIER_TIMESTAMP)
    @Mapping(target = "lockedStake", qualifiedByName = QUALIFIER_LOCKED_STAKE)
    @Mapping(target = "publicKey", qualifiedByName = QUALIFIER_BYTES)
    @Mapping(target = "salt", qualifiedByName = QUALIFIER_BYTES)
    @Mapping(target = "signature", qualifiedByName = QUALIFIER_BYTES)
    @Mapping(source = "timestampRange", target = "timestamp")
    @Override
    org.hiero.mirror.rest.model.ClprConnector map(ClprConnector connector);

    /** The stake is a string in the API since it's a tinybar amount, which can exceed what a JSON number holds. */
    @Named(QUALIFIER_LOCKED_STAKE)
    default String mapLockedStake(Long lockedStake) {
        return lockedStake != null ? lockedStake.toString() : null;
    }

    /** The generated enum names the constant {@code ECDSA_SECP256_K1}, though its JSON value is unchanged. */
    @ValueMapping(source = "ECDSA_SECP256K1", target = "ECDSA_SECP256_K1")
    org.hiero.mirror.rest.model.ClprSignatureScheme mapSignatureScheme(ClprSignatureScheme source);
}
