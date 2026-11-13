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
        return new ClprPeerThrottles()
                .maxGasPerMessage(configuration.getMaxGasPerMessage())
                .maxLocalEndpoints(configuration.getMaxLocalEndpoints())
                .maxMessagePayloadBytes(configuration.getMaxMessagePayloadBytes())
                .maxMessagesPerBundle(configuration.getMaxMessagesPerBundle())
                .maxPeerEndpoints(configuration.getMaxPeerEndpoints())
                .maxQueueDepth(configuration.getMaxQueueDepth())
                .maxSyncBytes(configuration.getMaxSyncBytes());
    }
}
