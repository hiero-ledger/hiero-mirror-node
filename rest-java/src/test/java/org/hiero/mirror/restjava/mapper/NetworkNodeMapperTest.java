// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.rest.model.ServiceEndpoint;
import org.hiero.mirror.restjava.dto.NetworkNodeDto;
import org.junit.jupiter.api.Test;

class NetworkNodeMapperTest {

    private final NetworkNodeMapper mapper = new NetworkNodeMapperImpl(new CommonMapperImpl());

    @Test
    void map() {
        // Given - row with all fields populated
        var row = mock(NetworkNodeDto.class);
        when(row.getNodeId()).thenReturn(3L);
        when(row.getFileId()).thenReturn(102L);
        when(row.getNodeAccountId()).thenReturn(8L);
        when(row.getNodeCertHash()).thenReturn("0xa1b2c3d4e5f6".getBytes(StandardCharsets.UTF_8));
        when(row.getPublicKey()).thenReturn("0x4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f");
        when(row.getStartConsensusTimestamp()).thenReturn(1000000000L);
        when(row.getEndConsensusTimestamp()).thenReturn(2000000000L);
        when(row.getStakingPeriod()).thenReturn(1609459200000000000L);
        when(row.getServiceEndpointsJson())
                .thenReturn("[{\"domain_name\":\"\",\"ip_address_v4\":\"192.168.1.1\",\"port\":50211}]");
        when(row.getGrpcProxyEndpointJson())
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

        // Given - row with null/empty values
        when(row.getFileId()).thenReturn(null);
        when(row.getNodeAccountId()).thenReturn(null);
        when(row.getNodeCertHash()).thenReturn(null);
        when(row.getStartConsensusTimestamp()).thenReturn(null);
        when(row.getEndConsensusTimestamp()).thenReturn(null);
        when(row.getStakingPeriod()).thenReturn(null);

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

        // Given - empty byte array
        when(row.getNodeCertHash()).thenReturn(new byte[0]);

        // When
        result = mapper.map(row);

        // Then
        assertThat(result.getNodeCertHash()).isEqualTo("0x");

        // Given - node cert hash without prefix
        when(row.getNodeCertHash()).thenReturn("1a2b3c4d5e6f".getBytes(StandardCharsets.UTF_8));

        // When
        result = mapper.map(row);

        // Then
        assertThat(result.getNodeCertHash()).isEqualTo("0x1a2b3c4d5e6f");
    }

    private ServiceEndpoint createServiceEndpoint(String ip, int port) {
        var endpoint = new ServiceEndpoint();
        endpoint.setIpAddressV4(ip);
        endpoint.setPort(port);
        return endpoint;
    }
}
