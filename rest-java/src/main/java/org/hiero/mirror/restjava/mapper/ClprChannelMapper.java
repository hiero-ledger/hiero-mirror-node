// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_TIMESTAMP;

import java.util.List;
import org.hiero.mirror.common.domain.clpr.ClprChannel;
import org.hiero.mirror.common.domain.clpr.ClprEndpoint;
import org.hiero.mirror.rest.model.ClprEndpointManifest;
import org.hiero.mirror.rest.model.ClprPeerThrottles;
import org.hiero.mirror.rest.model.ClprSignatureScheme;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ValueMapping;

@Mapper(config = MapperConfiguration.class)
public interface ClprChannelMapper extends CollectionMapper<ClprChannel, org.hiero.mirror.rest.model.ClprChannel> {

    @Mapping(source = "createdTimestamp", target = "createdTimestamp", qualifiedByName = QUALIFIER_TIMESTAMP)
    @Mapping(target = "endpointManifest", expression = "java(mapEndpointManifest(source))")
    @Mapping(source = "lastConfigTimestamp", target = "lastConfigTimestamp", qualifiedByName = QUALIFIER_TIMESTAMP)
    @Mapping(source = "peerConfigTimestamp", target = "peerConfigTimestamp", qualifiedByName = QUALIFIER_TIMESTAMP)
    @Mapping(target = "peerThrottles", expression = "java(mapPeerThrottles(source))")
    @Mapping(source = "timestampRange", target = "timestamp")
    org.hiero.mirror.rest.model.ClprChannel map(ClprChannel source);

    org.hiero.mirror.rest.model.ClprEndpoint mapEndpoint(ClprEndpoint source);

    default ClprEndpointManifest mapEndpointManifest(ClprChannel source) {
        final List<ClprEndpoint> endpoints = source.getEndpointManifest();
        final var manifest = new ClprEndpointManifest().version(source.getEndpointManifestVersion());
        if (endpoints == null) {
            return manifest.endpoints(List.of());
        }

        return manifest.endpoints(endpoints.stream().map(this::mapEndpoint).toList());
    }

    default ClprPeerThrottles mapPeerThrottles(ClprChannel source) {
        return new ClprPeerThrottles()
                .maxGasPerMessage(source.getPeerMaxGasPerMessage())
                .maxLocalEndpoints(source.getPeerMaxLocalEndpoints())
                .maxMessagePayloadBytes(source.getPeerMaxMessagePayloadBytes())
                .maxMessagesPerBundle(source.getPeerMaxMessagesPerBundle())
                .maxPeerEndpoints(source.getPeerMaxPeerEndpoints())
                .maxQueueDepth(source.getPeerMaxQueueDepth())
                .maxSyncBytes(source.getPeerMaxSyncBytes());
    }

    @ValueMapping(source = "ECDSA_SECP256K1", target = "ECDSA_SECP256_K1")
    ClprSignatureScheme mapSignatureScheme(org.hiero.mirror.common.domain.clpr.ClprSignatureScheme source);
}
