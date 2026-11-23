// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class ClprConnectorRepositoryTest extends ImporterIntegrationTest {

    private final ClprConnectorRepository clprConnectorRepository;

    @Test
    void findByChannelIdAndConnectorId() {
        var clprConnector = domainBuilder.clprConnector().persist();
        assertThat(clprConnectorRepository.findByChannelIdAndConnectorId(
                        clprConnector.getChannelId(), clprConnector.getConnectorId()))
                .get()
                .isEqualTo(clprConnector);
    }

    @Test
    void save() {
        var clprConnector = domainBuilder.clprConnector().persist();
        assertThat(clprConnectorRepository.findById(clprConnector.getId()))
                .get()
                .isEqualTo(clprConnector);
    }

    @Test
    void findCommitmentByChannelIdAndConnectorIdCacheHit() {
        // given
        final var connector = domainBuilder.clprConnector().persist();

        // when, then
        assertThat(clprConnectorRepository.findCommitmentByChannelIdAndConnectorId(
                        connector.getChannelId(), connector.getConnectorId()))
                .isEqualTo(connector.getCommitment());

        // then -- a second lookup after the row is gone can only succeed via the cache, not the repository
        clprConnectorRepository.deleteAll();
        assertThat(clprConnectorRepository.findCommitmentByChannelIdAndConnectorId(
                        connector.getChannelId(), connector.getConnectorId()))
                .isEqualTo(connector.getCommitment());
    }

    @Test
    void findCommitmentByChannelIdAndConnectorIdCacheMissNotFound() {
        // given
        final var channelId = domainBuilder.bytes(32);
        final var connectorId = domainBuilder.bytes(32);

        // when, then
        assertThat(clprConnectorRepository.findCommitmentByChannelIdAndConnectorId(channelId, connectorId))
                .isNull();

        // then -- the miss itself is cached, so a row persisted afterward still isn't found by a second lookup
        domainBuilder
                .clprConnector()
                .customize(c -> c.channelId(channelId).connectorId(connectorId))
                .persist();
        assertThat(clprConnectorRepository.findCommitmentByChannelIdAndConnectorId(channelId, connectorId))
                .isNull();
    }

    @Test
    void notifyWarmsCacheAheadOfOwnDbWrite() {
        // given -- the connector isn't in the repository yet, simulating a same-batch cold start
        final var connector = domainBuilder.clprConnector().get();

        // when
        clprConnectorRepository.notify(connector);

        // then
        assertThat(clprConnectorRepository.findCommitmentByChannelIdAndConnectorId(
                        connector.getChannelId(), connector.getConnectorId()))
                .isEqualTo(connector.getCommitment());
    }

    @Test
    void notifyWithoutConnectorIdIsNoOp() {
        // given -- a PENDING connector from ClprRegisterConnector, before ClprCompleteConnector reveals connectorId
        final var connector = domainBuilder
                .clprConnector()
                .customize(c -> c.connectorId(null))
                .get();

        // when
        clprConnectorRepository.notify(connector);

        // then -- nothing was cached, so a lookup for this channel still falls through to the (empty) repository
        assertThat(clprConnectorRepository.findCommitmentByChannelIdAndConnectorId(
                        connector.getChannelId(), domainBuilder.bytes(32)))
                .isNull();
    }
}
