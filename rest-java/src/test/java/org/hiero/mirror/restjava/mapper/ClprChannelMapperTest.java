// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import java.util.List;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.clpr.ClprChannelStatus;
import org.hiero.mirror.common.domain.clpr.ClprSignatureScheme;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.rest.model.ClprChannel;
import org.hiero.mirror.rest.model.ClprEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

final class ClprChannelMapperTest {

    private DomainBuilder domainBuilder;
    private ClprChannelMapper mapper;

    @BeforeEach
    void setup() {
        mapper = new ClprChannelMapperImpl(new CommonMapperImpl());
        domainBuilder = new DomainBuilder();
    }

    @Test
    void map() {
        // given
        final var source = domainBuilder
                .clprChannel()
                .customize(c -> c.timestampRange(Range.closedOpen(10L, 20L)))
                .get();
        final var common = new CommonMapperImpl();

        // when
        final var actual = mapper.map(source);

        // then
        assertThat(actual)
                .returns(source.getAckedMessageId(), ClprChannel::getAckedMessageId)
                .returns(source.getChainId(), ClprChannel::getChainId)
                .returns(common.mapByteArrayToHexString(source.getChannelContext()), ClprChannel::getChannelContext)
                .returns(common.mapByteArrayToHexString(source.getChannelId()), ClprChannel::getChannelId)
                .returns(common.mapByteArrayToHexString(source.getConfigProofBytes()), ClprChannel::getConfigProofBytes)
                .returns(DomainUtils.toTimestamp(source.getCreatedTimestamp()), ClprChannel::getCreatedTimestamp)
                .returns(
                        common.mapByteArrayToHexString(source.getEndpointManifestProofBytes()),
                        ClprChannel::getEndpointManifestProofBytes)
                .returns(source.getEndpointManifestVersion(), ClprChannel::getEndpointManifestVersion)
                .returns(DomainUtils.toTimestamp(source.getLastConfigTimestamp()), ClprChannel::getLastConfigTimestamp)
                .returns(source.getNextMessageId(), ClprChannel::getNextMessageId)
                .returns(
                        common.mapByteArrayToHexString(source.getOwnershipCommitment()),
                        ClprChannel::getOwnershipCommitment)
                .returns(DomainUtils.toTimestamp(source.getPeerConfigTimestamp()), ClprChannel::getPeerConfigTimestamp)
                .returns(common.mapByteArrayToHexString(source.getPublicKey()), ClprChannel::getPublicKey)
                .returns(source.getReceivedMessageId(), ClprChannel::getReceivedMessageId)
                .returns(
                        common.mapByteArrayToHexString(source.getReceivedRunningHash()),
                        ClprChannel::getReceivedRunningHash)
                .returns(common.mapByteArrayToHexString(source.getSentRunningHash()), ClprChannel::getSentRunningHash)
                .returns(common.mapByteArrayToHexString(source.getSignature()), ClprChannel::getSignature)
                .returns(org.hiero.mirror.rest.model.ClprSignatureScheme.ED25519, ClprChannel::getSignatureScheme)
                .returns(org.hiero.mirror.rest.model.ClprChannelStatus.ACTIVE, ClprChannel::getStatus)
                .returns(common.mapByteArrayToHexString(source.getTrustAnchor()), ClprChannel::getTrustAnchor)
                .returns(common.mapByteArrayToHexString(source.getTrustAnchorId()), ClprChannel::getTrustAnchorId)
                .returns(source.getVerifierContractId().toString(), ClprChannel::getVerifierContractId)
                .returns(
                        common.mapByteArrayToHexString(source.getVerifierFingerprint()),
                        ClprChannel::getVerifierFingerprint);
        assertThat(actual.getTimestamp())
                .returns(DomainUtils.toTimestamp(10L), t -> t.getFrom())
                .returns(DomainUtils.toTimestamp(20L), t -> t.getTo());
        assertThat(actual.getPeerThrottles())
                .returns(15_000_000L, t -> t.getMaxGasPerMessage())
                .returns(39L, t -> t.getMaxLocalEndpoints())
                .returns(8192L, t -> t.getMaxMessagePayloadBytes())
                .returns(100L, t -> t.getMaxMessagesPerBundle())
                .returns(39L, t -> t.getMaxPeerEndpoints())
                .returns(1000L, t -> t.getMaxQueueDepth())
                .returns(4_194_304L, t -> t.getMaxSyncBytes());

        final var endpoint = source.getEndpointManifest().getFirst();
        assertThat(actual.getEndpointManifest().getVersion()).isEqualTo(source.getEndpointManifestVersion());
        assertThat(actual.getEndpointManifest().getEndpoints())
                .singleElement()
                .returns(common.mapByteArrayToHexString(endpoint.getAccountId()), ClprEndpoint::getAccountId)
                .returns(common.mapByteArrayToHexString(endpoint.getTlsCertificate()), ClprEndpoint::getTlsCertificate)
                .satisfies(e -> assertThat(e.getServiceEndpoint())
                        .returns("10.0.0.5", s -> s.getIpAddress())
                        .returns(50211, s -> s.getPort()));
    }

    @Test
    void mapNulls() {
        // given
        final var source = domainBuilder
                .clprChannel()
                .customize(c -> c.channelContext(null)
                        .endpointManifest(null)
                        .lastConfigTimestamp(null)
                        .peerConfigTimestamp(null)
                        .signature(null)
                        .signatureScheme(null)
                        .verifierContractId(null))
                .get();

        // when
        final var actual = mapper.map(source);

        // then
        assertThat(actual.getChannelContext()).isNull();
        assertThat(actual.getLastConfigTimestamp()).isNull();
        assertThat(actual.getPeerConfigTimestamp()).isNull();
        assertThat(actual.getSignature()).isNull();
        assertThat(actual.getSignatureScheme()).isNull();
        assertThat(actual.getVerifierContractId()).isNull();
        assertThat(actual.getEndpointManifest().getEndpoints()).isEmpty();
    }

    @Test
    void mapNullPeerThrottles() {
        // given
        final var source = domainBuilder
                .clprChannel()
                .customize(c -> c.peerMaxGasPerMessage(null)
                        .peerMaxLocalEndpoints(null)
                        .peerMaxMessagePayloadBytes(null)
                        .peerMaxMessagesPerBundle(null)
                        .peerMaxPeerEndpoints(null)
                        .peerMaxQueueDepth(null)
                        .peerMaxSyncBytes(null))
                .get();

        // when
        final var throttles = mapper.map(source).getPeerThrottles();

        // then
        assertThat(throttles).isNotNull();
        assertThat(throttles.getMaxGasPerMessage()).isNull();
        assertThat(throttles.getMaxLocalEndpoints()).isNull();
        assertThat(throttles.getMaxMessagePayloadBytes()).isNull();
        assertThat(throttles.getMaxMessagesPerBundle()).isNull();
        assertThat(throttles.getMaxPeerEndpoints()).isNull();
        assertThat(throttles.getMaxQueueDepth()).isNull();
        assertThat(throttles.getMaxSyncBytes()).isNull();
    }

    @Test
    void mapOpenEndedTimestampRange() {
        // given
        final var source = domainBuilder
                .clprChannel()
                .customize(c -> c.timestampRange(Range.atLeast(10L)))
                .get();

        // when
        final var actual = mapper.map(source).getTimestamp();

        // then
        assertThat(actual.getFrom()).isEqualTo(DomainUtils.toTimestamp(10L));
        assertThat(actual.getTo()).isNull();
    }

    @Test
    void mapMultipleEndpoints() {
        // given
        final var endpoint1 = org.hiero.mirror.common.domain.clpr.ClprEndpoint.builder()
                .accountId(new byte[] {1})
                .serviceEndpoint(new org.hiero.mirror.common.domain.clpr.ClprServiceEndpoint("10.0.0.1", 1))
                .tlsCertificate(new byte[] {2})
                .build();
        final var endpoint2 = org.hiero.mirror.common.domain.clpr.ClprEndpoint.builder()
                .accountId(new byte[] {3})
                .serviceEndpoint(new org.hiero.mirror.common.domain.clpr.ClprServiceEndpoint("10.0.0.2", 2))
                .tlsCertificate(new byte[] {4})
                .build();
        final var source = domainBuilder
                .clprChannel()
                .customize(c -> c.endpointManifest(List.of(endpoint1, endpoint2)))
                .get();

        // when
        final var endpoints = mapper.map(source).getEndpointManifest().getEndpoints();

        // then
        assertThat(endpoints).hasSize(2);
        assertThat(endpoints.get(0).getServiceEndpoint().getIpAddress()).isEqualTo("10.0.0.1");
        assertThat(endpoints.get(0).getServiceEndpoint().getPort()).isEqualTo(1);
        assertThat(endpoints.get(1).getServiceEndpoint().getIpAddress()).isEqualTo("10.0.0.2");
        assertThat(endpoints.get(1).getServiceEndpoint().getPort()).isEqualTo(2);
    }

    @Test
    void mapShortBytesArePaddedTo32Bytes() {
        // given
        final var source = domainBuilder
                .clprChannel()
                .customize(c -> c.publicKey(new byte[] {1, 2, 3}))
                .get();

        // when / then
        assertThat(mapper.map(source).getPublicKey()).isEqualTo("0x" + "0".repeat(58) + "010203");
    }

    @ParameterizedTest
    @EnumSource(ClprChannelStatus.class)
    void mapStatus(ClprChannelStatus status) {
        // given
        final var source =
                domainBuilder.clprChannel().customize(c -> c.status(status)).get();

        // when / then
        assertThat(mapper.map(source).getStatus().getValue()).isEqualTo(status.name());
    }

    @ParameterizedTest
    @EnumSource(ClprSignatureScheme.class)
    void mapSignatureScheme(ClprSignatureScheme scheme) {
        // given
        final var source = domainBuilder
                .clprChannel()
                .customize(c -> c.signatureScheme(scheme))
                .get();

        // when / then
        assertThat(mapper.map(source).getSignatureScheme().getValue()).isEqualTo(scheme.name());
    }

    @Test
    void mapList() {
        // given
        final var sources = List.of(
                domainBuilder.clprChannel().get(), domainBuilder.clprChannel().get());

        // when / then
        assertThat(mapper.map(sources)).hasSize(2);
    }
}
