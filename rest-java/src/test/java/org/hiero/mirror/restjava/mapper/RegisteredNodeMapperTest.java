// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import java.util.List;
import org.apache.commons.codec.DecoderException;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.rest.model.Key;
import org.hiero.mirror.rest.model.RegisteredNodeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class RegisteredNodeMapperTest {

    private RegisteredNodeMapper mapper;

    @BeforeEach
    void setup() {
        mapper = new RegisteredNodeMapperImpl(new CommonMapperImpl());
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
        assertThat(result.getRegisteredNodeId()).isEqualTo(1L);
        assertThat(result.getDescription()).isEqualTo("node-1");
        assertThat(result.getCreatedTimestamp()).isEqualTo(DomainUtils.toTimestamp(123456789012345678L));

        assertThat(result.getTimestamp()).isNotNull();
        assertThat(result.getTimestamp().getFrom()).isEqualTo(DomainUtils.toTimestamp(1L));
        assertThat(result.getTimestamp().getTo()).isEqualTo(DomainUtils.toTimestamp(100L));

        assertThat(result.getServiceEndpoints()).isNotNull().hasSize(1);
        final var mappedEndpoint = result.getServiceEndpoints().getFirst();
        assertThat(mappedEndpoint.getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(mappedEndpoint.getPort()).isEqualTo(443);
        assertThat(mappedEndpoint.getRequiresTls()).isTrue();
        assertThat(mappedEndpoint.getBlockNode()).isNotNull();
        assertThat(mappedEndpoint.getBlockNode().getEndpointApi())
                .isEqualTo(org.hiero.mirror.rest.model.BlockNodeEndpoint.EndpointApiEnum.STATUS);
        assertThat(mappedEndpoint.getType()).isEqualTo(RegisteredNodeType.BLOCK_NODE);

        assertThat(result.getType())
                .as("registered node aggregated types")
                .containsExactly(RegisteredNodeType.BLOCK_NODE);

        assertThat(result.getAdminKey().getType()).isEqualTo(Key.TypeEnum.ED25519);
        assertThat(result.getAdminKey().getKey()).hasSize(64);
        assertThat(result.getAdminKey().getKey())
                .isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
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
                .as("serviceEndpoints should be null")
                .isNull();
        assertThat(result.getTimestamp()).as("timestamp should be null").isNull();
    }
}
