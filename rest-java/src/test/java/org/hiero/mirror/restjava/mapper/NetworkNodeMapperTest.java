// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.rest.model.ServiceEndpoint;
import org.hiero.mirror.restjava.dto.NetworkNodeDto;
import org.junit.jupiter.api.Test;

final class NetworkNodeMapperTest {

    private final NetworkNodeMapper mapper = new NetworkNodeMapperImpl(new CommonMapperImpl());

    @Test
    void map() {
        // Given - row with all fields populated
        var row = mock(NetworkNodeDto.class);
        when(row.nodeId()).thenReturn(3L);
        when(row.fileId()).thenReturn(102L);
        when(row.nodeAccountId()).thenReturn(8L);
        when(row.nodeCertHash()).thenReturn("0xa1b2c3d4e5f6");
        when(row.publicKey()).thenReturn("0x4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f");
        when(row.startConsensusTimestamp()).thenReturn(1000000000L);
        when(row.endConsensusTimestamp()).thenReturn(2000000000L);
        when(row.stakingPeriod()).thenReturn(1609459200000000000L);
        when(row.serviceEndpointsJson())
                .thenReturn("[{\"domain_name\":\"\",\"ip_address_v4\":\"192.168.1.1\",\"port\":50211}]");
        when(row.grpcProxyEndpointJson())
                .thenReturn("{\"domain_name\":\"\",\"ip_address_v4\":\"10.0.0.1\",\"port\":8080}");

        // When
        var result = mapper.map(row);

        // Then - verify mapped values
        assertThat(result).isNotNull().satisfies(node -> {
            assertThat(node.getNodeId()).isEqualTo(3L);
            assertThat(node.getFileId()).isEqualTo("0.0.102");
            assertThat(node.getNodeAccountId()).isEqualTo("0.0.8");
            assertThat(node.getNodeCertHash()).isEqualTo("0xa1b2c3d4e5f6");
            assertThat(node.getPublicKey())
                    .isEqualTo("0x4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f");
            assertThat(node.getTimestamp()).isNotNull().satisfies(timestamp -> {
                assertThat(timestamp.getFrom()).isEqualTo(DomainUtils.toTimestamp(1000000000L));
                assertThat(timestamp.getTo()).isEqualTo(DomainUtils.toTimestamp(2000000000L));
            });
            assertThat(node.getStakingPeriod()).isNotNull().satisfies(period -> {
                assertThat(period.getFrom()).isEqualTo(DomainUtils.toTimestamp(1609459200000000001L));
                assertThat(period.getTo()).isEqualTo(DomainUtils.toTimestamp(1609545600000000001L));
            });
        });

        // Given - row with null/empty values (SQL query returns "0x" for null/empty node_cert_hash)
        when(row.fileId()).thenReturn(null);
        when(row.nodeAccountId()).thenReturn(null);
        when(row.nodeCertHash()).thenReturn("0x");
        when(row.startConsensusTimestamp()).thenReturn(null);
        when(row.endConsensusTimestamp()).thenReturn(null);
        when(row.stakingPeriod()).thenReturn(null);

        // When
        result = mapper.map(row);

        // Then - verify null handling
        assertThat(result).isNotNull().satisfies(node -> {
            assertThat(node.getFileId()).isNull();
            assertThat(node.getNodeAccountId()).isNull();
            assertThat(node.getNodeCertHash()).isEqualTo("0x");
            assertThat(node.getTimestamp()).isNull();
            assertThat(node.getStakingPeriod()).isNull();
        });
    }

    private ServiceEndpoint createServiceEndpoint(String ip, int port) {
        var endpoint = new ServiceEndpoint();
        endpoint.setIpAddressV4(ip);
        endpoint.setPort(port);
        return endpoint;
    }
}
