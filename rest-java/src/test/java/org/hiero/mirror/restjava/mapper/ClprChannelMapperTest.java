// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.hiero.mirror.common.converter.ObjectToStringSerializer;
import org.hiero.mirror.common.domain.clpr.ClprEndpoint;
import org.hiero.mirror.common.domain.clpr.ClprServiceEndpoint;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.restjava.dto.ClprChannelDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class ClprChannelMapperTest {

    private ClprChannelMapper mapper;

    @BeforeEach
    void setup() {
        mapper = new ClprChannelMapperImpl(new CommonMapperImpl());
    }

    @Test
    void map() {
        // given
        final var verifierContractId = EntityId.of(456L);
        final var channel = ClprChannelDto.builder()
                .ackedMessageId(47L)
                .chainId("eip155:1")
                .channelContext(new byte[] {0x1a, 0x2b})
                .channelId(new byte[] {0x01, 0x02, 0x03})
                .configProofBytes(new byte[] {0x11})
                .createdTimestamp(1_000_000_000L)
                .endpointManifest(json(ClprEndpoint.builder()
                        .serviceEndpoint(new ClprServiceEndpoint("10.0.0.5", 50211))
                        .build()))
                .endpointManifestProofBytes(new byte[] {0x33, 0x44})
                .endpointManifestVersion(3L)
                .lastConfigTimestamp(2_000_000_000L)
                .nextMessageId(48L)
                .ownershipCommitment(new byte[] {(byte) 0x7a, (byte) 0xb3})
                .peerConfigTimestamp(3_000_000_000L)
                .peerMaxGasPerMessage(15_000_000L)
                .peerMaxLocalEndpoints(39L)
                .peerMaxMessagePayloadBytes(8192L)
                .peerMaxMessagesPerBundle(100L)
                .peerMaxPeerEndpoints(39L)
                .peerMaxQueueDepth(1000L)
                .peerMaxSyncBytes(4_194_304L)
                .publicKey(new byte[] {0x04, (byte) 0xa1})
                .receivedMessageId(52L)
                .receivedRunningHash(new byte[] {(byte) 0xbb})
                .sentRunningHash(new byte[] {(byte) 0xaa})
                .signature(new byte[] {(byte) 0x9f, (byte) 0x8e})
                .signatureScheme("ED25519")
                .status("ACTIVE")
                .timestampLower(4_000_000_000L)
                .trustAnchor(new byte[] {0x33})
                .trustAnchorId(new byte[] {(byte) 0x9f})
                .verifierContractId(verifierContractId.getId())
                .verifierFingerprint(new byte[] {0x55, (byte) 0xaa, (byte) 0xbb})
                .build();

        // when
        final var actual = mapper.map(channel);

        // then
        assertThat(actual.getAckedMessageId()).isEqualTo(47L);
        assertThat(actual.getChainId()).isEqualTo("eip155:1");
        assertThat(actual.getChannelContext()).isEqualTo("0x1a2b");
        assertThat(actual.getChannelId()).isEqualTo("0x010203");
        assertThat(actual.getConfigProofBytes()).isEqualTo("0x11");
        assertThat(actual.getCreatedTimestamp()).isEqualTo("1.000000000");
        assertThat(actual.getEndpointManifestProofBytes()).isEqualTo("0x3344");
        assertThat(actual.getEndpointManifestVersion()).isEqualTo(3L);
        assertThat(actual.getLastConfigTimestamp()).isEqualTo("2.000000000");
        assertThat(actual.getNextMessageId()).isEqualTo(48L);
        assertThat(actual.getOwnershipCommitment()).isEqualTo("0x7ab3");
        assertThat(actual.getPeerConfigTimestamp()).isEqualTo("3.000000000");
        assertThat(actual.getPublicKey()).isEqualTo("0x04a1");
        assertThat(actual.getReceivedMessageId()).isEqualTo(52L);
        assertThat(actual.getReceivedRunningHash()).isEqualTo("0xbb");
        assertThat(actual.getSentRunningHash()).isEqualTo("0xaa");
        assertThat(actual.getSignature()).isEqualTo("0x9f8e");
        assertThat(actual.getSignatureScheme().getValue()).isEqualTo("ED25519");
        assertThat(actual.getStatus().getValue()).isEqualTo("ACTIVE");
        assertThat(actual.getTimestamp().getFrom()).isEqualTo("4.000000000");
        assertThat(actual.getTimestamp().getTo()).isNull();
        assertThat(actual.getTrustAnchor()).isEqualTo("0x33");
        assertThat(actual.getTrustAnchorId()).isEqualTo("0x9f");
        assertThat(actual.getVerifierContractId()).isEqualTo(verifierContractId.toString());
        assertThat(actual.getVerifierFingerprint()).isEqualTo("0x55aabb");

        assertThat(actual.getPeerThrottles()).satisfies(throttles -> {
            assertThat(throttles.getMaxGasPerMessage()).isEqualTo(15_000_000L);
            assertThat(throttles.getMaxLocalEndpoints()).isEqualTo(39L);
            assertThat(throttles.getMaxMessagePayloadBytes()).isEqualTo(8192L);
            assertThat(throttles.getMaxMessagesPerBundle()).isEqualTo(100L);
            assertThat(throttles.getMaxPeerEndpoints()).isEqualTo(39L);
            assertThat(throttles.getMaxQueueDepth()).isEqualTo(1000L);
            assertThat(throttles.getMaxSyncBytes()).isEqualTo(4_194_304L);
        });

        assertThat(actual.getEndpointManifest()).satisfies(manifest -> {
            assertThat(manifest.getVersion()).isEqualTo(3L);
            assertThat(manifest.getEndpoints())
                    .containsExactly(Map.of("service_endpoint", Map.of("ip_address", "10.0.0.5", "port", 50211)));
        });
    }

    @Test
    void mapPending() {
        // given
        final var pending = ClprChannelDto.builder()
                .createdTimestamp(1_000_000_000L)
                .ownershipCommitment(new byte[] {0x7a, (byte) 0xb3})
                .status("PENDING")
                .build();

        // when
        final var actual = mapper.map(pending);

        // then
        assertThat(actual.getCreatedTimestamp()).isEqualTo("1.000000000");
        assertThat(actual.getOwnershipCommitment()).isEqualTo("0x7ab3");
        assertThat(actual.getStatus().getValue()).isEqualTo("PENDING");
        assertThat(actual.getAckedMessageId()).isNull();
        assertThat(actual.getChainId()).isNull();
        assertThat(actual.getChannelId()).isNull();
        assertThat(actual.getEndpointManifest()).isNull();
        assertThat(actual.getPeerThrottles()).isNull();
        assertThat(actual.getTimestamp()).isNull();
    }

    @Test
    void mapSerializesToDesignDocResponse() throws Exception {
        // given: the channel from the design doc's response example
        final var channel = ClprChannelDto.builder()
                .ackedMessageId(47L)
                .chainId("eip155:1")
                .channelContext(new byte[] {0x1a, 0x2b, 0x3c})
                .channelId(new byte[] {0x1a, 0x2b, 0x3c})
                .configProofBytes(new byte[] {0x11, 0x22})
                .createdTimestamp(1726874200_000000000L)
                .endpointManifest(json(ClprEndpoint.builder()
                        .serviceEndpoint(new ClprServiceEndpoint("10.0.0.5", 50211))
                        .build()))
                .endpointManifestProofBytes(new byte[] {0x33, 0x44})
                .endpointManifestVersion(3L)
                .lastConfigTimestamp(1726874345_123456789L)
                .nextMessageId(48L)
                .ownershipCommitment(new byte[] {0x7a, (byte) 0xb3, (byte) 0xf1})
                .peerConfigTimestamp(1726874300_000000000L)
                .peerMaxGasPerMessage(15_000_000L)
                .peerMaxLocalEndpoints(39L)
                .peerMaxMessagePayloadBytes(8192L)
                .peerMaxMessagesPerBundle(100L)
                .peerMaxPeerEndpoints(39L)
                .peerMaxQueueDepth(1000L)
                .peerMaxSyncBytes(4_194_304L)
                .publicKey(new byte[] {0x04, (byte) 0xa1, (byte) 0xb2, (byte) 0xc3})
                .receivedMessageId(52L)
                .receivedRunningHash(new byte[] {(byte) 0xbb, 0x22})
                .sentRunningHash(new byte[] {(byte) 0xaa, 0x11})
                .signature(new byte[] {(byte) 0x9f, (byte) 0x8e, 0x7d, 0x6c})
                .signatureScheme("ED25519")
                .status("ACTIVE")
                .timestampLower(1726874345_123456789L)
                .trustAnchor(new byte[] {0x33, 0x44})
                .trustAnchorId(new byte[] {(byte) 0x9f, (byte) 0x8e})
                .verifierContractId(456L)
                .verifierFingerprint(new byte[] {0x55, (byte) 0xaa, (byte) 0xbb})
                .build();
        final var expected = """
                {
                  "acked_message_id": 47,
                  "chain_id": "eip155:1",
                  "channel_context": "0x1a2b3c",
                  "channel_id": "0x1a2b3c",
                  "config_proof_bytes": "0x1122",
                  "created_timestamp": "1726874200.000000000",
                  "endpoint_manifest": {
                    "endpoints": [{"service_endpoint": {"ip_address": "10.0.0.5", "port": 50211}}],
                    "version": 3
                  },
                  "endpoint_manifest_proof_bytes": "0x3344",
                  "endpoint_manifest_version": 3,
                  "last_config_timestamp": "1726874345.123456789",
                  "next_message_id": 48,
                  "ownership_commitment": "0x7ab3f1",
                  "peer_config_timestamp": "1726874300.000000000",
                  "peer_throttles": {
                    "max_gas_per_message": 15000000,
                    "max_local_endpoints": 39,
                    "max_message_payload_bytes": 8192,
                    "max_messages_per_bundle": 100,
                    "max_peer_endpoints": 39,
                    "max_queue_depth": 1000,
                    "max_sync_bytes": 4194304
                  },
                  "public_key": "0x04a1b2c3",
                  "received_message_id": 52,
                  "received_running_hash": "0xbb22",
                  "sent_running_hash": "0xaa11",
                  "signature": "0x9f8e7d6c",
                  "signature_scheme": "ED25519",
                  "status": "ACTIVE",
                  "timestamp": {"from": "1726874345.123456789", "to": null},
                  "trust_anchor": "0x3344",
                  "trust_anchor_id": "0x9f8e",
                  "verifier_contract_id": "0.0.456",
                  "verifier_fingerprint": "0x55aabb"
                }
                """;

        // when
        final var objectMapper = new ObjectMapper();
        final var actual = objectMapper.readTree(objectMapper.writeValueAsString(mapper.map(channel)));

        // then
        assertThat(actual).isEqualTo(objectMapper.readTree(expected));
    }

    @Test
    void mapEndpointManifestIncludesOptionalEndpointFields() {
        // given
        final var channel = ClprChannelDto.builder()
                .endpointManifest(json(ClprEndpoint.builder()
                        .accountId(new byte[] {0x01})
                        .serviceEndpoint(new ClprServiceEndpoint("10.0.0.5", 50211))
                        .tlsCertificate(new byte[] {0x02, 0x03})
                        .build()))
                .endpointManifestVersion(1L)
                .build();

        // when
        final var actual = mapper.map(channel);

        // then
        assertThat(actual.getEndpointManifest().getEndpoints())
                .containsExactly(Map.of(
                        "account_id",
                        "0x01",
                        "service_endpoint",
                        Map.of("ip_address", "10.0.0.5", "port", 50211),
                        "tls_certificate",
                        "0x0203"));
    }

    @Test
    void mapSignatureSchemes() {
        // when / then
        assertThat(mapper.mapSignatureScheme("ECDSA_SECP256K1"))
                .extracting(scheme -> scheme.getValue())
                .isEqualTo("ECDSA_SECP256K1");
        assertThat(mapper.mapSignatureScheme("ED25519"))
                .extracting(scheme -> scheme.getValue())
                .isEqualTo("ED25519");
        assertThat(mapper.mapSignatureScheme(null)).isNull();
    }

    @Test
    void mapDoesNotPadShortBytes() {
        // given
        final var channel =
                ClprChannelDto.builder().signature(new byte[] {0x01}).build();

        // when / then
        assertThat(mapper.map(channel).getSignature()).isEqualTo("0x01");
    }

    @Test
    void mapNullableFields() {
        // given
        final var channel = ClprChannelDto.builder()
                .channelId(new byte[] {0x01})
                .status("PAUSED")
                .build();

        // when
        final var actual = mapper.map(channel);

        // then
        assertThat(actual.getChannelId()).isEqualTo("0x01");
        assertThat(actual.getStatus().getValue()).isEqualTo("PAUSED");
        assertThat(actual.getEndpointManifest()).isNull();
        assertThat(actual.getPeerThrottles()).isNull();
        assertThat(actual.getPublicKey()).isNull();
        assertThat(actual.getSignatureScheme()).isNull();
        assertThat(actual.getTimestamp()).isNull();
        assertThat(actual.getVerifierContractId()).isNull();
    }

    /** Serializes the endpoints the way they're stored in the jsonb column. */
    private static String json(ClprEndpoint... endpoints) {
        try {
            return ObjectToStringSerializer.OBJECT_MAPPER.writeValueAsString(List.of(endpoints));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
