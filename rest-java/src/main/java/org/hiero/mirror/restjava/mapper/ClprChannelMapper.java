// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_BYTES;
import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_TIMESTAMP;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.hiero.mirror.common.converter.ObjectToStringSerializer;
import org.hiero.mirror.common.domain.clpr.ClprEndpoint;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.rest.model.ClprChannel;
import org.hiero.mirror.rest.model.ClprChannelEndpointManifest;
import org.hiero.mirror.rest.model.ClprPeerThrottles;
import org.hiero.mirror.rest.model.ClprSignatureScheme;
import org.hiero.mirror.rest.model.TimestampRangeNullable;
import org.hiero.mirror.restjava.dto.ClprChannelDto;
import org.hiero.mirror.restjava.exception.InvalidMappingException;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps channels and open pending channel commitments to the API model. A pending commitment only has
 * {@code created_timestamp}, {@code ownership_commitment}, and a {@code PENDING} status, so the rest is null.
 */
@Mapper(config = MapperConfiguration.class)
public interface ClprChannelMapper extends CollectionMapper<ClprChannelDto, ClprChannel> {

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
    @Mapping(target = "lastConfigTimestamp", qualifiedByName = QUALIFIER_TIMESTAMP)
    @Mapping(target = "peerConfigTimestamp", qualifiedByName = QUALIFIER_TIMESTAMP)
    @Mapping(target = "endpointManifest", expression = "java(mapEndpointManifest(channel))")
    @Mapping(target = "peerThrottles", expression = "java(mapPeerThrottles(channel))")
    @Mapping(target = "timestamp", expression = "java(mapTimestampRange(channel))")
    @Override
    ClprChannel map(ClprChannelDto channel);

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

    /** Builds the API's {@code {endpoints, version}} object from the stored endpoint list JSON and version. */
    default ClprChannelEndpointManifest mapEndpointManifest(ClprChannelDto channel) {
        final var json = channel.endpointManifest();
        if (json == null || json.isEmpty()) {
            return null;
        }

        final List<ClprEndpoint> endpoints;
        try {
            endpoints = ObjectToStringSerializer.OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new InvalidMappingException("Failed to parse the endpoint manifest", e);
        }

        final var mapped = new ArrayList<Map<String, Object>>(endpoints.size());
        for (final var endpoint : endpoints) {
            mapped.add(mapEndpoint(endpoint));
        }

        return new ClprChannelEndpointManifest().endpoints(mapped).version(channel.endpointManifestVersion());
    }

    default ClprPeerThrottles mapPeerThrottles(ClprChannelDto channel) {
        if (channel.peerMaxGasPerMessage() == null
                && channel.peerMaxLocalEndpoints() == null
                && channel.peerMaxMessagePayloadBytes() == null
                && channel.peerMaxMessagesPerBundle() == null
                && channel.peerMaxPeerEndpoints() == null
                && channel.peerMaxQueueDepth() == null
                && channel.peerMaxSyncBytes() == null) {
            return null;
        }

        return new ClprPeerThrottles()
                .maxGasPerMessage(channel.peerMaxGasPerMessage())
                .maxLocalEndpoints(channel.peerMaxLocalEndpoints())
                .maxMessagePayloadBytes(channel.peerMaxMessagePayloadBytes())
                .maxMessagesPerBundle(channel.peerMaxMessagesPerBundle())
                .maxPeerEndpoints(channel.peerMaxPeerEndpoints())
                .maxQueueDepth(channel.peerMaxQueueDepth())
                .maxSyncBytes(channel.peerMaxSyncBytes());
    }

    default TimestampRangeNullable mapTimestampRange(ClprChannelDto channel) {
        final var lower = channel.timestampLower();
        final var upper = channel.timestampUpper();
        if (lower == null && upper == null) {
            return null;
        }

        return new TimestampRangeNullable()
                .from(lower != null ? DomainUtils.toTimestamp(lower) : null)
                .to(upper != null ? DomainUtils.toTimestamp(upper) : null);
    }

    /** The generated enum names the constant {@code ECDSA_SECP256_K1}, though its JSON value is unchanged. */
    default ClprSignatureScheme mapSignatureScheme(String source) {
        return switch (source) {
            case null -> null;
            case "ECDSA_SECP256K1" -> ClprSignatureScheme.ECDSA_SECP256_K1;
            default -> ClprSignatureScheme.fromValue(source);
        };
    }

    private static String hex(byte[] source) {
        return source != null ? "0x" + HexFormat.of().formatHex(source) : null;
    }
}
