// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.rest.model.Links;
import org.hiero.mirror.rest.model.NetworkNode;
import org.hiero.mirror.rest.model.NetworkNodesResponse;
import org.hiero.mirror.rest.model.ServiceEndpoint;
import org.hiero.mirror.restjava.repository.NetworkNodeRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NetworkNodeMapperTest {

    private ObjectMapper objectMapper;
    private CommonMapper commonMapper;
    private TestNetworkNodeMapper mapper;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        commonMapper = new CommonMapperImpl();
        mapper = new TestNetworkNodeMapper(objectMapper, commonMapper);
    }

    @Test
    void map() throws Exception {
        // Given
        var row = mockNetworkNodeRow();
        when(row.getDescription()).thenReturn("Test Node");
        when(row.getMemo()).thenReturn("Test Memo");
        when(row.getNodeId()).thenReturn(3L);
        when(row.getNodeAccountId()).thenReturn("8");
        when(row.getPublicKey()).thenReturn("abcd1234");
        when(row.getNodeCertHash()).thenReturn("hash123".getBytes(StandardCharsets.UTF_8));
        when(row.getFileId()).thenReturn(102L);
        when(row.getStartConsensusTimestamp()).thenReturn(1000000000L);
        when(row.getEndConsensusTimestamp()).thenReturn(2000000000L);
        when(row.getAdminKey()).thenReturn(new byte[] {1, 2, 3});
        when(row.getDeclineReward()).thenReturn(false);
        when(row.getMaxStake()).thenReturn(50000000000L);
        when(row.getMinStake()).thenReturn(10000000000L);
        when(row.getRewardRateStart()).thenReturn(100000L);
        when(row.getStake()).thenReturn(25000000000L);
        when(row.getStakeNotRewarded()).thenReturn(1000000000L);
        when(row.getStakeRewarded()).thenReturn(24000000000L);
        when(row.getStakingPeriod()).thenReturn(1609459200000000000L); // 2021-01-01 00:00:00 UTC

        var serviceEndpoint = new ServiceEndpoint();
        serviceEndpoint.setIpAddressV4("192.168.1.1");
        serviceEndpoint.setPort(50211);
        var serviceEndpointsJson = objectMapper.writeValueAsString(List.of(serviceEndpoint));
        when(row.getServiceEndpoints()).thenReturn(serviceEndpointsJson);

        var grpcProxyEndpoint = new ServiceEndpoint();
        grpcProxyEndpoint.setIpAddressV4("10.0.0.1");
        grpcProxyEndpoint.setPort(8080);
        var grpcProxyJson = objectMapper.writeValueAsString(grpcProxyEndpoint);
        when(row.getGrpcProxyEndpoint()).thenReturn(grpcProxyJson);

        // When
        var result = mapper.map(row);

        // Then
        assertThat(result)
                .returns("Test Node", NetworkNode::getDescription)
                .returns("Test Memo", NetworkNode::getMemo)
                .returns(3L, NetworkNode::getNodeId)
                .returns("0.0.8", NetworkNode::getNodeAccountId)
                .returns("0xabcd1234", NetworkNode::getPublicKey)
                .returns("0xhash123", NetworkNode::getNodeCertHash)
                .returns("0.0.102", NetworkNode::getFileId)
                .returns(false, NetworkNode::getDeclineReward)
                .returns(50000000000L, NetworkNode::getMaxStake)
                .returns(10000000000L, NetworkNode::getMinStake)
                .returns(100000L, NetworkNode::getRewardRateStart)
                .returns(25000000000L, NetworkNode::getStake)
                .returns(1000000000L, NetworkNode::getStakeNotRewarded)
                .returns(24000000000L, NetworkNode::getStakeRewarded);

        assertThat(result.getAdminKey()).isNotNull().returns("010203", org.hiero.mirror.rest.model.Key::getKey);

        assertThat(result.getServiceEndpoints())
                .hasSize(1)
                .first()
                .returns("192.168.1.1", ServiceEndpoint::getIpAddressV4)
                .returns(50211, ServiceEndpoint::getPort);

        assertThat(result.getGrpcProxyEndpoint())
                .returns("10.0.0.1", ServiceEndpoint::getIpAddressV4)
                .returns(8080, ServiceEndpoint::getPort);

        assertThat(result.getTimestamp())
                .isNotNull()
                .returns(DomainUtils.toTimestamp(1000000000L), org.hiero.mirror.rest.model.TimestampRange::getFrom)
                .returns(DomainUtils.toTimestamp(2000000000L), org.hiero.mirror.rest.model.TimestampRange::getTo);

        assertThat(result.getStakingPeriod())
                .isNotNull()
                .returns(
                        DomainUtils.toTimestamp(1609459200000000001L),
                        org.hiero.mirror.rest.model.TimestampRangeNullable::getFrom)
                .returns(
                        DomainUtils.toTimestamp(1609545600000000001L),
                        org.hiero.mirror.rest.model.TimestampRangeNullable::getTo);
    }

    @Test
    void mapWithNullValues() throws Exception {
        // Given
        var row = mockNetworkNodeRow();
        when(row.getDescription()).thenReturn(null);
        when(row.getMemo()).thenReturn(null);
        when(row.getNodeId()).thenReturn(1L);
        when(row.getNodeAccountId()).thenReturn(null);
        when(row.getPublicKey()).thenReturn(null);
        when(row.getNodeCertHash()).thenReturn(null);
        when(row.getFileId()).thenReturn(102L);
        when(row.getStartConsensusTimestamp()).thenReturn(1000L);
        when(row.getEndConsensusTimestamp()).thenReturn(2000L);
        when(row.getAdminKey()).thenReturn(null);
        when(row.getDeclineReward()).thenReturn(null);
        when(row.getMaxStake()).thenReturn(null);
        when(row.getMinStake()).thenReturn(null);
        when(row.getRewardRateStart()).thenReturn(null);
        when(row.getStake()).thenReturn(null);
        when(row.getStakeNotRewarded()).thenReturn(null);
        when(row.getStakeRewarded()).thenReturn(null);
        when(row.getStakingPeriod()).thenReturn(null);
        when(row.getGrpcProxyEndpoint()).thenReturn(null);
        when(row.getServiceEndpoints()).thenReturn("[]");

        // When
        var result = mapper.map(row);

        // Then
        assertThat(result)
                .returns(null, NetworkNode::getDescription)
                .returns(null, NetworkNode::getMemo)
                .returns(1L, NetworkNode::getNodeId)
                .returns(null, NetworkNode::getNodeAccountId)
                .returns("0x", NetworkNode::getPublicKey)
                .returns("0x", NetworkNode::getNodeCertHash)
                .returns("0.0.102", NetworkNode::getFileId)
                .returns(null, NetworkNode::getDeclineReward)
                .returns(null, NetworkNode::getMaxStake)
                .returns(null, NetworkNode::getMinStake)
                .returns(null, NetworkNode::getRewardRateStart)
                .returns(null, NetworkNode::getStake)
                .returns(null, NetworkNode::getStakeNotRewarded)
                .returns(null, NetworkNode::getStakeRewarded)
                .returns(null, NetworkNode::getAdminKey)
                .returns(null, NetworkNode::getGrpcProxyEndpoint)
                .returns(null, NetworkNode::getStakingPeriod);

        assertThat(result.getServiceEndpoints()).isEmpty();
    }

    @Test
    void mapWithMinusOneStakeValues() throws Exception {
        // Given - stake values of -1 should be mapped to null
        var row = mockNetworkNodeRow();
        when(row.getNodeId()).thenReturn(1L);
        when(row.getFileId()).thenReturn(102L);
        when(row.getStartConsensusTimestamp()).thenReturn(1000L);
        when(row.getEndConsensusTimestamp()).thenReturn(2000L);
        when(row.getMaxStake()).thenReturn(-1L);
        when(row.getMinStake()).thenReturn(-1L);
        when(row.getStakeNotRewarded()).thenReturn(-1L);
        when(row.getServiceEndpoints()).thenReturn("[]");

        // When
        var result = mapper.map(row);

        // Then
        assertThat(result)
                .returns(null, NetworkNode::getMaxStake)
                .returns(null, NetworkNode::getMinStake)
                .returns(null, NetworkNode::getStakeNotRewarded);
    }

    @Test
    void mapWithEmptyStrings() throws Exception {
        // Given
        var row = mockNetworkNodeRow();
        when(row.getNodeId()).thenReturn(1L);
        when(row.getFileId()).thenReturn(102L);
        when(row.getStartConsensusTimestamp()).thenReturn(1000L);
        when(row.getEndConsensusTimestamp()).thenReturn(2000L);
        when(row.getPublicKey()).thenReturn("");
        when(row.getNodeCertHash()).thenReturn(new byte[0]);
        when(row.getGrpcProxyEndpoint()).thenReturn("");
        when(row.getServiceEndpoints()).thenReturn("[]");

        // When
        var result = mapper.map(row);

        // Then
        assertThat(result)
                .returns("0x", NetworkNode::getPublicKey)
                .returns("0x", NetworkNode::getNodeCertHash)
                .returns(null, NetworkNode::getGrpcProxyEndpoint);
    }

    @Test
    void mapWithExistingHexPrefix() throws Exception {
        // Given - values already have 0x prefix
        var row = mockNetworkNodeRow();
        when(row.getNodeId()).thenReturn(1L);
        when(row.getFileId()).thenReturn(102L);
        when(row.getStartConsensusTimestamp()).thenReturn(1000L);
        when(row.getEndConsensusTimestamp()).thenReturn(2000L);
        when(row.getPublicKey()).thenReturn("0xabcd");
        when(row.getNodeCertHash()).thenReturn("0x1234".getBytes(StandardCharsets.UTF_8));
        when(row.getServiceEndpoints()).thenReturn("[]");

        // When
        var result = mapper.map(row);

        // Then - addHexPrefix checks for existing prefix and doesn't double it
        assertThat(result).returns("0xabcd", NetworkNode::getPublicKey).returns("0x1234", NetworkNode::getNodeCertHash);
    }

    @Test
    void mapWithInvalidJson() {
        // Given - invalid JSON for service endpoints
        var row = mockNetworkNodeRow();
        when(row.getNodeId()).thenReturn(1L);
        when(row.getFileId()).thenReturn(102L);
        when(row.getStartConsensusTimestamp()).thenReturn(1000L);
        when(row.getEndConsensusTimestamp()).thenReturn(2000L);
        when(row.getServiceEndpoints()).thenReturn("invalid json");

        // When/Then
        assertThatThrownBy(() -> mapper.map(row))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to map NetworkNodeRow to NetworkNode");
    }

    @Test
    void mapToResponse() {
        // Given
        var node1 = new NetworkNode();
        node1.setNodeId(1L);
        var node2 = new NetworkNode();
        node2.setNodeId(2L);
        var nodes = List.of(node1, node2);
        var links = new Links();

        // When
        var response = mapper.mapToResponse(nodes, links);

        // Then
        assertThat(response)
                .isNotNull()
                .returns(nodes, NetworkNodesResponse::getNodes)
                .returns(links, NetworkNodesResponse::getLinks);
    }

    @Test
    void mapStake() {
        // Given/When/Then
        assertThat(mapper.mapStake(null)).isNull();
        assertThat(mapper.mapStake(-1L)).isNull();
        assertThat(mapper.mapStake(0L)).isEqualTo(0L);
        assertThat(mapper.mapStake(100L)).isEqualTo(100L);
        assertThat(mapper.mapStake(Long.MAX_VALUE)).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void addHexPrefix() {
        // Given/When/Then
        assertThat(mapper.addHexPrefix(null)).isEqualTo("0x");
        assertThat(mapper.addHexPrefix("")).isEqualTo("0x");
        assertThat(mapper.addHexPrefix("abcd")).isEqualTo("0xabcd");
        assertThat(mapper.addHexPrefix("0xabcd")).isEqualTo("0xabcd");
        assertThat(mapper.addHexPrefix("123456")).isEqualTo("0x123456");
    }

    @Test
    void mapStakingPeriod() {
        // Given/When/Then - null staking period
        assertThat(mapper.mapStakingPeriod(null)).isNull();

        // Given - valid staking period (2021-01-01 00:00:00 UTC)
        long stakingPeriod = 1609459200000000000L;

        // When
        var result = mapper.mapStakingPeriod(stakingPeriod);

        // Then
        assertThat(result)
                .isNotNull()
                .returns(
                        DomainUtils.toTimestamp(stakingPeriod + 1L),
                        org.hiero.mirror.rest.model.TimestampRangeNullable::getFrom)
                .returns(
                        DomainUtils.toTimestamp(stakingPeriod + 1L + (86400L * DomainUtils.NANOS_PER_SECOND)),
                        org.hiero.mirror.rest.model.TimestampRangeNullable::getTo);
    }

    @Test
    void mapTimestampRange() {
        // Given
        long startTimestamp = 1000000000L;
        long endTimestamp = 2000000000L;

        // When
        var result = mapper.mapTimestampRange(startTimestamp, endTimestamp);

        // Then
        assertThat(result)
                .isNotNull()
                .returns(DomainUtils.toTimestamp(startTimestamp), org.hiero.mirror.rest.model.TimestampRange::getFrom)
                .returns(DomainUtils.toTimestamp(endTimestamp), org.hiero.mirror.rest.model.TimestampRange::getTo);
    }

    @Test
    void mapFileIdConversion() throws Exception {
        // Given - fileId as Long should be converted to EntityId format
        var row = mockNetworkNodeRow();
        when(row.getNodeId()).thenReturn(1L);
        when(row.getFileId()).thenReturn(102L);
        when(row.getStartConsensusTimestamp()).thenReturn(1000L);
        when(row.getEndConsensusTimestamp()).thenReturn(2000L);
        when(row.getServiceEndpoints()).thenReturn("[]");

        // When
        var result = mapper.map(row);

        // Then
        assertThat(result.getFileId()).isEqualTo("0.0.102");
    }

    @Test
    void mapFileIdNull() throws Exception {
        // Given - null fileId should remain null
        var row = mockNetworkNodeRow();
        when(row.getNodeId()).thenReturn(1L);
        when(row.getFileId()).thenReturn(null);
        when(row.getStartConsensusTimestamp()).thenReturn(1000L);
        when(row.getEndConsensusTimestamp()).thenReturn(2000L);
        when(row.getServiceEndpoints()).thenReturn("[]");

        // When
        var result = mapper.map(row);

        // Then
        assertThat(result.getFileId()).isNull();
    }

    @Test
    void mapFileIdDifferentValues() throws Exception {
        // Given - various fileId values
        var row = mockNetworkNodeRow();
        when(row.getNodeId()).thenReturn(1L);
        when(row.getStartConsensusTimestamp()).thenReturn(1000L);
        when(row.getEndConsensusTimestamp()).thenReturn(2000L);
        when(row.getServiceEndpoints()).thenReturn("[]");

        // Test fileId = 101
        when(row.getFileId()).thenReturn(101L);
        assertThat(mapper.map(row).getFileId()).isEqualTo("0.0.101");

        // Test fileId = 102
        when(row.getFileId()).thenReturn(102L);
        assertThat(mapper.map(row).getFileId()).isEqualTo("0.0.102");

        // Test fileId = 111
        when(row.getFileId()).thenReturn(111L);
        assertThat(mapper.map(row).getFileId()).isEqualTo("0.0.111");

        // Test fileId = 112
        when(row.getFileId()).thenReturn(112L);
        assertThat(mapper.map(row).getFileId()).isEqualTo("0.0.112");
    }

    private NetworkNodeRow mockNetworkNodeRow() {
        return mock(NetworkNodeRow.class);
    }

    /**
     * Concrete test implementation of NetworkNodeMapper that allows dependency injection for testing.
     */
    private static class TestNetworkNodeMapper extends NetworkNodeMapper {
        TestNetworkNodeMapper(ObjectMapper objectMapper, CommonMapper commonMapper) {
            this.objectMapper = objectMapper;
            this.commonMapper = commonMapper;
        }
    }
}
