// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_BYTES;
import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_TIMESTAMP;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.hiero.mirror.common.domain.clpr.ClprChannel;
import org.hiero.mirror.common.domain.clpr.ClprEndpoint;
import org.hiero.mirror.common.domain.clpr.ClprSignatureScheme;
import org.hiero.mirror.rest.model.ClprChannelEndpointManifest;
import org.hiero.mirror.rest.model.ClprPeerThrottles;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ValueMapping;

/**
 * Maps channels to the API model. See {@link ClprPendingChannelMapper} for pending channel commitments.
 */
@Mapper(config = MapperConfiguration.class)
public interface ClprChannelMapper extends CollectionMapper<ClprChannel, org.hiero.mirror.rest.model.ClprChannel> {

    @Mapping(target = "channelContext", qualifiedByName = QUALIFIER_BYTES)
    @Mapping(target = "channelId", qualifiedByName = QUALIFIER_BYTES)
    @Mapping(target = "configProofBytes", qualifiedByName = QUALIFIER_BYTES)
    @Mapping(target = "endpointManifestProofBytes", qualifiedByName = QUALIFIER_BYTES)
    @Mapping(target = "ownershipCommitment", qualifiedByName = QUALIFIER_BYTES)
    @Mapping(target = "publicKey", qualifiedByName = QUALIFIER_BYTES)
    @Mapping(target = "receivedRunningHash", qualifiedByName = QUALIFIER_BYTES)
    @Mapping(target = "sentRunningHash", qualifiedByName = QUALIFIER_BYTES)
    @Mapping(target = "signature", qualifiedByName = QUALIFIER_BYTES)
    @Mapping(target = "trustAnchor", qualifiedByName = QUALIFIER_BYTES)
    @Mapping(target = "trustAnchorId", qualifiedByName = QUALIFIER_BYTES)
    @Mapping(target = "verifierFingerprint", qualifiedByName = QUALIFIER_BYTES)
    @Mapping(target = "createdTimestamp", qualifiedByName = QUALIFIER_TIMESTAMP)
    @Mapping(target = "endpointManifest", expression = "java(mapEndpointManifest(channel))")
    @Mapping(target = "lastConfigTimestamp", qualifiedByName = QUALIFIER_TIMESTAMP)
    @Mapping(target = "peerConfigTimestamp", qualifiedByName = QUALIFIER_TIMESTAMP)
    @Mapping(target = "peerThrottles", expression = "java(mapPeerThrottles(channel))")
    @Mapping(source = "timestampRange", target = "timestamp")
    @Override
    org.hiero.mirror.rest.model.ClprChannel map(ClprChannel channel);

    default Map<String, Object> mapEndpoint(ClprEndpoint endpoint) {
        final var map = new LinkedHashMap<String, Object>();
        map.put("account_id", hex(endpoint.getAccountId()));

        final var serviceEndpoint = endpoint.getServiceEndpoint();
        if (serviceEndpoint != null) {
            final var serviceEndpointMap = new LinkedHashMap<String, Object>();
            serviceEndpointMap.put("ip_address", serviceEndpoint.ipAddress());
            serviceEndpointMap.put("port", serviceEndpoint.port());
            map.put("service_endpoint", serviceEndpointMap);
        }

        map.put("tls_certificate", hex(endpoint.getTlsCertificate()));
        map.values().removeIf(Objects::isNull);
        return map;
    }

    /** Builds the API's {@code {endpoints, version}} object from the stored endpoint list and version. */
    default ClprChannelEndpointManifest mapEndpointManifest(ClprChannel channel) {
        final var endpoints = channel.getEndpointManifest();
        if (endpoints == null) {
            return null;
        }

        final var mapped = new ArrayList<Map<String, Object>>(endpoints.size());
        for (final var endpoint : endpoints) {
            mapped.add(mapEndpoint(endpoint));
        }

        return new ClprChannelEndpointManifest().endpoints(mapped).version(channel.getEndpointManifestVersion());
    }

    default ClprPeerThrottles mapPeerThrottles(ClprChannel channel) {
        if (channel.getPeerMaxGasPerMessage() == null
                && channel.getPeerMaxLocalEndpoints() == null
                && channel.getPeerMaxMessagePayloadBytes() == null
                && channel.getPeerMaxMessagesPerBundle() == null
                && channel.getPeerMaxPeerEndpoints() == null
                && channel.getPeerMaxQueueDepth() == null
                && channel.getPeerMaxSyncBytes() == null) {
            return null;
        }

        return new ClprPeerThrottles()
                .maxGasPerMessage(channel.getPeerMaxGasPerMessage())
                .maxLocalEndpoints(channel.getPeerMaxLocalEndpoints())
                .maxMessagePayloadBytes(channel.getPeerMaxMessagePayloadBytes())
                .maxMessagesPerBundle(channel.getPeerMaxMessagesPerBundle())
                .maxPeerEndpoints(channel.getPeerMaxPeerEndpoints())
                .maxQueueDepth(channel.getPeerMaxQueueDepth())
                .maxSyncBytes(channel.getPeerMaxSyncBytes());
    }

    /** The generated enum names the constant {@code ECDSA_SECP256_K1}, though its JSON value is unchanged. */
    @ValueMapping(source = "ECDSA_SECP256K1", target = "ECDSA_SECP256_K1")
    org.hiero.mirror.rest.model.ClprSignatureScheme mapSignatureScheme(ClprSignatureScheme source);

    private static String hex(byte[] source) {
        return source != null ? "0x" + HexFormat.of().formatHex(source) : null;
    }
}
