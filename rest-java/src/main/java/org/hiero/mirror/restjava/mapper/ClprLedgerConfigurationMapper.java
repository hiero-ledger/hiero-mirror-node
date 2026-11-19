// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_BYTES;
import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_TIMESTAMP;

import org.hiero.mirror.common.domain.clpr.ClprLedgerConfiguration;
import org.hiero.mirror.rest.model.ClprPeerThrottles;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapperConfiguration.class)
public interface ClprLedgerConfigurationMapper
        extends CollectionMapper<ClprLedgerConfiguration, org.hiero.mirror.rest.model.ClprLedgerConfiguration> {

    @Mapping(target = "consensusTimestamp", qualifiedByName = QUALIFIER_TIMESTAMP)
    @Mapping(target = "initialTrustAnchor", qualifiedByName = QUALIFIER_BYTES)
    @Mapping(target = "initialTrustAnchorId", qualifiedByName = QUALIFIER_BYTES)
    @Mapping(target = "serviceAddress", qualifiedByName = QUALIFIER_BYTES)
    @Mapping(target = "throttles", expression = "java(mapThrottles(configuration))")
    @Override
    org.hiero.mirror.rest.model.ClprLedgerConfiguration map(ClprLedgerConfiguration configuration);

    default ClprPeerThrottles mapThrottles(ClprLedgerConfiguration configuration) {
        final var throttle = configuration.getThrottle();
        if (throttle == null) {
            return new ClprPeerThrottles();
        }

        return new ClprPeerThrottles()
                .maxGasPerMessage(throttle.getMaxGasPerMessage())
                .maxLocalEndpoints(throttle.getMaxLocalEndpoints())
                .maxMessagePayloadBytes(throttle.getMaxMessagePayloadBytes())
                .maxMessagesPerBundle(throttle.getMaxMessagesPerBundle())
                .maxPeerEndpoints(throttle.getMaxPeerEndpoints())
                .maxQueueDepth(throttle.getMaxQueueDepth())
                .maxSyncBytes(throttle.getMaxSyncBytes());
    }
}
