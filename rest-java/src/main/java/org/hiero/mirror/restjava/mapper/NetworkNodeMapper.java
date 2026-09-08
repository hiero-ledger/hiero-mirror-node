// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import org.hiero.mirror.common.converter.ObjectToStringSerializer;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.rest.model.NetworkNode;
import org.hiero.mirror.rest.model.ServiceEndpoint;
import org.hiero.mirror.rest.model.TimestampRange;
import org.hiero.mirror.rest.model.TimestampRangeNullable;
import org.hiero.mirror.restjava.dto.NetworkNodeDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(config = MapperConfiguration.class)
public interface NetworkNodeMapper extends CollectionMapper<NetworkNodeDto, NetworkNode> {

    String HEX_PREFIX = "0x";

    @Override
    @Mapping(target = "grpcProxyEndpoint", expression = "java(parseServiceEndpoint(row.grpcProxyEndpointJson()))")
    @Mapping(target = "nodeCertHash", expression = "java(mapNodeCertHash(row.nodeCertHash()))")
    @Mapping(target = "serviceEndpoints", expression = "java(parseServiceEndpointList(row.serviceEndpointsJson()))")
    @Mapping(target = "stakingPeriod", qualifiedByName = "mapStakingPeriod")
    @Mapping(target = "timestamp", expression = "java(mapTimestampRange(row))")
    NetworkNode map(NetworkNodeDto row);

    /**
     * Renders the raw {@code node_cert_hash} bytea as a "0x"-prefixed string. The bytes are chain-controlled and, by
     * convention, the ASCII hex of the certificate's SHA-384 hash, but nothing upstream enforces that. Decoding is done
     * with {@link StandardCharsets#UTF_8}, which substitutes invalid byte sequences with U+FFFD rather than throwing,
     * matching the historical JS behavior ({@code Buffer.toString('utf8')}) and keeping GET /network/nodes available
     * even when a poisoned address book entry holds non-UTF-8 bytes.
     */
    default String mapNodeCertHash(byte[] nodeCertHash) {
        if (nodeCertHash == null || nodeCertHash.length == 0) {
            return HEX_PREFIX;
        }
        final var decoded = new String(nodeCertHash, StandardCharsets.UTF_8);
        return decoded.startsWith(HEX_PREFIX) ? decoded : HEX_PREFIX + decoded;
    }

    default TimestampRange mapTimestampRange(NetworkNodeDto row) {
        if (row == null) {
            return null;
        }
        final var start = row.startConsensusTimestamp();
        final var end = row.endConsensusTimestamp();
        if (start == null && end == null) {
            return null;
        }
        return new TimestampRange()
                .from(start != null ? DomainUtils.toTimestamp(start) : null)
                .to(end != null ? DomainUtils.toTimestamp(end) : null);
    }

    @Named("mapStakingPeriod")
    default TimestampRangeNullable mapStakingPeriod(Long stakingPeriod) {
        if (stakingPeriod == null) {
            return null;
        }
        final var from = stakingPeriod + 1L;
        return new TimestampRangeNullable()
                .from(DomainUtils.toTimestamp(from))
                .to(DomainUtils.toTimestamp(from + (86400L * DomainUtils.NANOS_PER_SECOND)));
    }

    default ServiceEndpoint parseServiceEndpoint(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return ObjectToStringSerializer.OBJECT_MAPPER.readValue(json, ServiceEndpoint.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse service endpoint", e);
        }
    }

    default List<ServiceEndpoint> parseServiceEndpointList(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return ObjectToStringSerializer.OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse service endpoints", e);
        }
    }
}
