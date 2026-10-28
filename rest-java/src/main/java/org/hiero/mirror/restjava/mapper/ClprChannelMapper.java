// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_TIMESTAMP;

import org.hiero.mirror.common.domain.clpr.ClprChannel;
import org.hiero.mirror.rest.model.ClprSignatureScheme;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ValueMapping;

@Mapper(config = MapperConfiguration.class)
public interface ClprChannelMapper extends CollectionMapper<ClprChannel, org.hiero.mirror.rest.model.ClprChannel> {

    @Mapping(source = "createdTimestamp", target = "createdTimestamp", qualifiedByName = QUALIFIER_TIMESTAMP)
    @Mapping(
            target = "endpointManifest.endpoints",
            source = "endpointManifest",
            defaultExpression = "java(java.util.List.of())")
    @Mapping(target = "endpointManifest.version", source = "endpointManifestVersion")
    @Mapping(source = "lastConfigTimestamp", target = "lastConfigTimestamp", qualifiedByName = QUALIFIER_TIMESTAMP)
    @Mapping(source = "peerConfigTimestamp", target = "peerConfigTimestamp", qualifiedByName = QUALIFIER_TIMESTAMP)
    @Mapping(target = "peerThrottles.maxGasPerMessage", source = "peerMaxGasPerMessage")
    @Mapping(target = "peerThrottles.maxLocalEndpoints", source = "peerMaxLocalEndpoints")
    @Mapping(target = "peerThrottles.maxMessagePayloadBytes", source = "peerMaxMessagePayloadBytes")
    @Mapping(target = "peerThrottles.maxMessagesPerBundle", source = "peerMaxMessagesPerBundle")
    @Mapping(target = "peerThrottles.maxPeerEndpoints", source = "peerMaxPeerEndpoints")
    @Mapping(target = "peerThrottles.maxQueueDepth", source = "peerMaxQueueDepth")
    @Mapping(target = "peerThrottles.maxSyncBytes", source = "peerMaxSyncBytes")
    @Mapping(source = "timestampRange", target = "timestamp")
    org.hiero.mirror.rest.model.ClprChannel map(ClprChannel source);

    @ValueMapping(source = "ECDSA_SECP256K1", target = "ECDSA_SECP256_K1")
    ClprSignatureScheme mapSignatureScheme(org.hiero.mirror.common.domain.clpr.ClprSignatureScheme source);
}
