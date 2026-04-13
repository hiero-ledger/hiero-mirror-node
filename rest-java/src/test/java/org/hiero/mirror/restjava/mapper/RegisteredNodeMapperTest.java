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
                        .endpointApi(RegisteredServiceEndpoint.BlockNodeApi.STATUS)
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

        assertThat(result.getType())
                .as("registered node aggregated types")
                .containsExactlyElementsOf(registeredNode.getType().stream()
                        .map(commonMapper::mapRegisteredNodeType)
                        .toList());

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
        assertThat(result.getType()).as("type should be empty list").isEmpty();
        assertThat(result.getTimestamp()).as("timestamp should be null").isNull();
    }

    @Test
    void mapServiceEndpoints() {
        assertThat(mapper.mapServiceEndpoints(null)).isEmpty();

        final var domainEndpoint = RegisteredServiceEndpoint.builder()
                .blockNode(RegisteredServiceEndpoint.BlockNodeEndpoint.builder()
                        .endpointApi(RegisteredServiceEndpoint.BlockNodeApi.STATUS)
                        .build())
                .ipAddress("10.0.0.1")
                .port(8080)
                .requiresTls(false)
                .build();

        final var actual = mapper.mapServiceEndpoints(List.of(domainEndpoint));

        assertThat(actual).containsExactly(mapper.toRegisteredServiceEndpoint(domainEndpoint));
    }

    @Test
    void mapRegisteredNodeTypes() {
        assertThat(mapper.mapRegisteredNodeTypes(null, commonMapper)).isEmpty();

        final var typeIds = List.of((short) 0, (short) 1, (short) 2, (short) 3, (short) 4, (short) 99);
        final var expected =
                typeIds.stream().map(commonMapper::mapRegisteredNodeType).toList();

        final var actual = mapper.mapRegisteredNodeTypes(typeIds, commonMapper);

        assertThat(actual).containsExactlyElementsOf(expected);
    }

    @Test
    void toRegisteredServiceEndpoint() {
        assertThat(mapper.toRegisteredServiceEndpoint(null)).isNull();

        final var domainEndpoint = RegisteredServiceEndpoint.builder()
                .blockNode(RegisteredServiceEndpoint.BlockNodeEndpoint.builder()
                        .endpointApi(RegisteredServiceEndpoint.BlockNodeApi.STATUS)
                        .build())
                .ipAddress("192.168.0.1")
                .port(443)
                .requiresTls(true)
                .build();

        final var actual = mapper.toRegisteredServiceEndpoint(domainEndpoint);

        assertThat(actual.getIpAddress()).isEqualTo(domainEndpoint.getIpAddress());
        assertThat(actual.getPort()).isEqualTo(domainEndpoint.getPort());
        assertThat(actual.getRequiresTls()).isEqualTo(domainEndpoint.isRequiresTls());
        assertThat(actual.getBlockNode()).isEqualTo(commonMapper.blockNodeForRest(domainEndpoint.getBlockNode()));
        assertThat(actual.getType())
                .isEqualTo(commonMapper.mapRegisteredNodeType(
                        domainEndpoint.getType().getId()));
    }
}
