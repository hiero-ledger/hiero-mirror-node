// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import java.util.List;
import org.apache.commons.codec.DecoderException;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint;
import org.hiero.mirror.common.util.DomainUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class RegisteredNodeMapperTest {

    private CommonMapper commonMapper;
    private RegisteredNodeMapper mapper;

    @BeforeEach
    void setup() {
        commonMapper = new CommonMapperImpl();
        mapper = new RegisteredNodeMapperImpl(commonMapper);
    }

    @Test
    void map() throws DecoderException {
        // given
        final var serviceEndpoint = RegisteredServiceEndpoint.builder()
                .blockNode(RegisteredServiceEndpoint.BlockNodeEndpoint.builder()
                        .endpointApis(List.of(RegisteredServiceEndpoint.BlockNodeApi.STATUS))
                        .build())
                .ipAddress("127.0.0.1")
                .port(443)
                .requiresTls(true)
                .build();

        final var ed25519Hex = "1220" + "a".repeat(64);
        final var registeredNode = RegisteredNode.builder()
                .adminKey(org.apache.commons.codec.binary.Hex.decodeHex(ed25519Hex))
                .createdTimestamp(123456789012345678L)
                .deleted(false)
                .description("node-1")
                .registeredNodeId(1L)
                .serviceEndpoints(List.of(serviceEndpoint))
                .timestampRange(Range.openClosed(1L, 100L))
                .type(List.of((short) 1))
                .build();

        // when
        final var result = mapper.map(registeredNode);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getRegisteredNodeId()).isEqualTo(registeredNode.getRegisteredNodeId());
        assertThat(result.getDescription()).isEqualTo(registeredNode.getDescription());
        assertThat(result.getCreatedTimestamp())
                .isEqualTo(DomainUtils.toTimestamp(registeredNode.getCreatedTimestamp()));

        assertThat(result.getTimestamp()).isNotNull();
        assertThat(result.getTimestamp().getFrom())
                .isEqualTo(DomainUtils.toTimestamp(
                        registeredNode.getTimestampRange().lowerEndpoint()));
        assertThat(result.getTimestamp().getTo())
                .isEqualTo(DomainUtils.toTimestamp(
                        registeredNode.getTimestampRange().upperEndpoint()));

        assertThat(result.getServiceEndpoints())
                .isNotNull()
                .hasSize(registeredNode.getServiceEndpoints().size());
        assertThat(result.getServiceEndpoints().getFirst())
                .isEqualTo(mapper.toRegisteredServiceEndpoint(
                        registeredNode.getServiceEndpoints().getFirst()));
        assertThat(result.getAdminKey()).isEqualTo(commonMapper.mapKey(registeredNode.getAdminKey()));
    }

    @Test
    void mapNulls() {
        // given
        final var source = new RegisteredNode();

        // when
        final var result = mapper.map(source);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getAdminKey()).as("adminKey should be null").isNull();
        assertThat(result.getCreatedTimestamp())
                .as("createdTimestamp should be null")
                .isNull();
        assertThat(result.getDescription()).as("description should be null").isNull();
        assertThat(result.getRegisteredNodeId())
                .as("registeredNodeId should be null")
                .isNull();
        assertThat(result.getServiceEndpoints())
                .as("serviceEndpoints should be empty list")
                .isEmpty();
        assertThat(result.getTimestamp()).as("timestamp should be null").isNull();
    }

    @Test
    void mapServiceEndpoints() {
        assertThat(mapper.mapServiceEndpoints(null)).isEmpty();

        final var domainEndpoint = RegisteredServiceEndpoint.builder()
                .blockNode(RegisteredServiceEndpoint.BlockNodeEndpoint.builder()
                        .endpointApis(List.of(RegisteredServiceEndpoint.BlockNodeApi.STATUS))
                        .build())
                .ipAddress("10.0.0.1")
                .port(8080)
                .requiresTls(false)
                .build();

        final var actual = mapper.mapServiceEndpoints(List.of(domainEndpoint));

        assertThat(actual).containsExactly(mapper.toRegisteredServiceEndpoint(domainEndpoint));
    }

    @Test
    void toRegisteredServiceEndpoint() {
        assertThat(mapper.toRegisteredServiceEndpoint(null)).isNull();

        final var domainEndpoint = RegisteredServiceEndpoint.builder()
                .blockNode(RegisteredServiceEndpoint.BlockNodeEndpoint.builder()
                        .endpointApis(List.of(RegisteredServiceEndpoint.BlockNodeApi.STATUS))
                        .build())
                .ipAddress("192.168.0.1")
                .port(443)
                .requiresTls(true)
                .build();

        final var actual = mapper.toRegisteredServiceEndpoint(domainEndpoint);

        assertThat(actual.getIpAddress()).isEqualTo(domainEndpoint.getIpAddress());
        assertThat(actual.getPort()).isEqualTo(domainEndpoint.getPort());
        assertThat(actual.getRequiresTls()).isEqualTo(domainEndpoint.isRequiresTls());
        assertThat(actual.getType())
                .isEqualTo(mapper.mapRegisteredNodeType(domainEndpoint.getType().getId()));
    }

    @Test
    void toRegisteredServiceEndpointWithMultipleApis() {
        final var ipAddress = "192.168.1.10";
        final var port = 50211;
        final var requiresTls = true;
        final var endpointApis =
                List.of(RegisteredServiceEndpoint.BlockNodeApi.STATUS, RegisteredServiceEndpoint.BlockNodeApi.PUBLISH);
        final var domainEndpoint = RegisteredServiceEndpoint.builder()
                .blockNode(RegisteredServiceEndpoint.BlockNodeEndpoint.builder()
                        .endpointApis(endpointApis)
                        .build())
                .ipAddress(ipAddress)
                .port(port)
                .requiresTls(requiresTls)
                .build();

        final var actual = mapper.toRegisteredServiceEndpoint(domainEndpoint);

        assertThat(actual.getIpAddress()).isEqualTo(domainEndpoint.getIpAddress());
        assertThat(actual.getPort()).isEqualTo(domainEndpoint.getPort());
        assertThat(actual.getRequiresTls()).isEqualTo(domainEndpoint.isRequiresTls());
        assertThat(actual.getBlockNode()).isNotNull();
        assertThat(actual.getBlockNode().getEndpointApis())
                .containsExactly(
                        org.hiero.mirror.rest.model.RegisteredBlockNodeApi.STATUS,
                        org.hiero.mirror.rest.model.RegisteredBlockNodeApi.PUBLISH);
    }

    @Test
    void toRegisteredServiceEndpointWithAllApis() {
        final var ipAddress = "10.20.30.40";
        final var port = 8443;
        final var requiresTls = false;
        final var endpointApis = List.of(
                RegisteredServiceEndpoint.BlockNodeApi.OTHER,
                RegisteredServiceEndpoint.BlockNodeApi.STATUS,
                RegisteredServiceEndpoint.BlockNodeApi.PUBLISH,
                RegisteredServiceEndpoint.BlockNodeApi.SUBSCRIBE_STREAM,
                RegisteredServiceEndpoint.BlockNodeApi.STATE_PROOF);
        final var domainEndpoint = RegisteredServiceEndpoint.builder()
                .blockNode(RegisteredServiceEndpoint.BlockNodeEndpoint.builder()
                        .endpointApis(endpointApis)
                        .build())
                .ipAddress(ipAddress)
                .port(port)
                .requiresTls(requiresTls)
                .build();

        final var actual = mapper.toRegisteredServiceEndpoint(domainEndpoint);

        assertThat(actual.getIpAddress()).isEqualTo(domainEndpoint.getIpAddress());
        assertThat(actual.getPort()).isEqualTo(domainEndpoint.getPort());
        assertThat(actual.getRequiresTls()).isEqualTo(domainEndpoint.isRequiresTls());
        assertThat(actual.getBlockNode()).isNotNull();
        assertThat(actual.getBlockNode().getEndpointApis())
                .containsExactly(
                        org.hiero.mirror.rest.model.RegisteredBlockNodeApi.OTHER,
                        org.hiero.mirror.rest.model.RegisteredBlockNodeApi.STATUS,
                        org.hiero.mirror.rest.model.RegisteredBlockNodeApi.PUBLISH,
                        org.hiero.mirror.rest.model.RegisteredBlockNodeApi.SUBSCRIBE_STREAM,
                        org.hiero.mirror.rest.model.RegisteredBlockNodeApi.STATE_PROOF);
    }

    @Test
    void mapWithMultipleServiceEndpoints() throws DecoderException {
        final var ip1 = "192.168.1.10";
        final var port1 = 50211;
        final var requiresTls1 = true;
        final var endpoint1 = RegisteredServiceEndpoint.builder()
                .blockNode(RegisteredServiceEndpoint.BlockNodeEndpoint.builder()
                        .endpointApis(List.of(
                                RegisteredServiceEndpoint.BlockNodeApi.STATUS,
                                RegisteredServiceEndpoint.BlockNodeApi.PUBLISH))
                        .build())
                .ipAddress(ip1)
                .port(port1)
                .requiresTls(requiresTls1)
                .build();

        final var ip2 = "10.0.0.5";
        final var port2 = 50211;
        final var requiresTls2 = false;
        final var endpoint2 = RegisteredServiceEndpoint.builder()
                .blockNode(RegisteredServiceEndpoint.BlockNodeEndpoint.builder()
                        .endpointApis(List.of(RegisteredServiceEndpoint.BlockNodeApi.STATUS))
                        .build())
                .ipAddress(ip2)
                .port(port2)
                .requiresTls(requiresTls2)
                .build();

        final var ed25519Hex = "1220" + "b".repeat(64);
        final var registeredNode = RegisteredNode.builder()
                .adminKey(org.apache.commons.codec.binary.Hex.decodeHex(ed25519Hex))
                .createdTimestamp(123456789012345678L)
                .deleted(false)
                .description("sample-block-node")
                .registeredNodeId(1001L)
                .serviceEndpoints(List.of(endpoint1, endpoint2))
                .timestampRange(Range.openClosed(1000000000000000000L, 2000000000000000000L))
                .type(List.of((short) 1))
                .build();

        final var result = mapper.map(registeredNode);

        assertThat(result).isNotNull();
        assertThat(result.getRegisteredNodeId()).isEqualTo(registeredNode.getRegisteredNodeId());
        assertThat(result.getDescription()).isEqualTo(registeredNode.getDescription());
        assertThat(result.getServiceEndpoints())
                .hasSize(registeredNode.getServiceEndpoints().size());

        final var mappedEndpoint1 = result.getServiceEndpoints().get(0);
        assertThat(mappedEndpoint1.getIpAddress()).isEqualTo(endpoint1.getIpAddress());
        assertThat(mappedEndpoint1.getPort()).isEqualTo(endpoint1.getPort());
        assertThat(mappedEndpoint1.getRequiresTls()).isEqualTo(endpoint1.isRequiresTls());
        assertThat(mappedEndpoint1.getBlockNode().getEndpointApis())
                .containsExactly(
                        org.hiero.mirror.rest.model.RegisteredBlockNodeApi.STATUS,
                        org.hiero.mirror.rest.model.RegisteredBlockNodeApi.PUBLISH);

        final var mappedEndpoint2 = result.getServiceEndpoints().get(1);
        assertThat(mappedEndpoint2.getIpAddress()).isEqualTo(endpoint2.getIpAddress());
        assertThat(mappedEndpoint2.getPort()).isEqualTo(endpoint2.getPort());
        assertThat(mappedEndpoint2.getRequiresTls()).isEqualTo(endpoint2.isRequiresTls());
        assertThat(mappedEndpoint2.getBlockNode().getEndpointApis())
                .containsExactly(org.hiero.mirror.rest.model.RegisteredBlockNodeApi.STATUS);
    }
}
