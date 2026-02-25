// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import java.nio.charset.StandardCharsets;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.rest.model.NetworkNode;
import org.hiero.mirror.rest.model.TimestampRange;
import org.hiero.mirror.rest.model.TimestampRangeNullable;
import org.hiero.mirror.restjava.converter.StringToServiceEndpointConverter;
import org.hiero.mirror.restjava.converter.StringToServiceEndpointListConverter;
import org.hiero.mirror.restjava.dto.NetworkNodeDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(
        config = MapperConfiguration.class,
        imports = {StringToServiceEndpointConverter.class, StringToServiceEndpointListConverter.class})
public interface NetworkNodeMapper extends CollectionMapper<NetworkNodeDto, NetworkNode> {

    @Override
    @Mapping(
            target = "nodeAccountId",
            expression = "java(row.nodeAccountId() != null ? commonMapper.mapEntityId(row.nodeAccountId()) : null)")
    @Mapping(target = "nodeCertHash", qualifiedByName = "mapNodeCertHash")
    @Mapping(
            target = "grpcProxyEndpoint",
            expression = "java(StringToServiceEndpointConverter.INSTANCE.convert(row.grpcProxyEndpointJson()))")
    @Mapping(
            target = "serviceEndpoints",
            expression = "java(StringToServiceEndpointListConverter.INSTANCE.convert(row.serviceEndpointsJson()))")
    @Mapping(target = "stakingPeriod", qualifiedByName = "mapStakingPeriod")
    @Mapping(target = "timestamp", expression = "java(mapTimestampRange(row))")
    NetworkNode map(NetworkNodeDto row);

    @Named("mapNodeCertHash")
    default String mapNodeCertHash(byte[] nodeCertHash) {
        if (nodeCertHash == null || nodeCertHash.length == 0) {
            return "0x";
        }
        var hexString = new String(nodeCertHash, StandardCharsets.UTF_8);
        return hexString.startsWith("0x") ? hexString : "0x" + hexString;
    }

    default TimestampRange mapTimestampRange(NetworkNodeDto row) {
        if (row == null) {
            return null;
        }
        var start = row.startConsensusTimestamp();
        var end = row.endConsensusTimestamp();
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
}
