// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.config.CacheConfiguration.CACHE_NAME;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.importer.repository.ClprConnectorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.caffeine.CaffeineCacheManager;

@ExtendWith(MockitoExtension.class)
final class ClprConnectorServiceTest {

    private final DomainBuilder domainBuilder = new DomainBuilder();

    @Mock
    private ClprConnectorRepository clprConnectorRepository;

    private ClprConnectorService clprConnectorService;

    @BeforeEach
    void setup() {
        final var cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(Set.of(CACHE_NAME));
        clprConnectorService = new ClprConnectorService(cacheManager, clprConnectorRepository);
    }

    @Test
    void lookupCacheMiss() {
        // given
        final var channelId = domainBuilder.bytes(32);
        final var connectorId = domainBuilder.bytes(32);
        final var commitment = domainBuilder.bytes(32);
        when(clprConnectorRepository.findCommitmentByChannelIdAndConnectorId(channelId, connectorId))
                .thenReturn(Optional.of(commitment));

        // when, then -- a second lookup hits the cache, not the repository again
        assertThat(clprConnectorService.lookup(channelId, connectorId)).isEqualTo(commitment);
        assertThat(clprConnectorService.lookup(channelId, connectorId)).isEqualTo(commitment);
        verify(clprConnectorRepository).findCommitmentByChannelIdAndConnectorId(channelId, connectorId);
    }

    @Test
    void lookupCacheMissNotFound() {
        // given
        final var channelId = domainBuilder.bytes(32);
        final var connectorId = domainBuilder.bytes(32);
        when(clprConnectorRepository.findCommitmentByChannelIdAndConnectorId(channelId, connectorId))
                .thenReturn(Optional.empty());

        // when, then -- the miss itself is cached, so a second lookup still doesn't hit the repository again
        assertThat(clprConnectorService.lookup(channelId, connectorId)).isNull();
        assertThat(clprConnectorService.lookup(channelId, connectorId)).isNull();
        verify(clprConnectorRepository).findCommitmentByChannelIdAndConnectorId(channelId, connectorId);
    }

    @Test
    void lookupLoaderExceptionReturnsNullInsteadOfPropagating() {
        // given
        final var channelId = domainBuilder.bytes(32);
        final var connectorId = domainBuilder.bytes(32);
        when(clprConnectorRepository.findCommitmentByChannelIdAndConnectorId(channelId, connectorId))
                .thenThrow(new RuntimeException("DB error"));

        // when, then
        assertThat(clprConnectorService.lookup(channelId, connectorId)).isNull();
    }

    @Test
    void notifyWarmsCacheAheadOfOwnDbWrite() {
        // given -- the connector isn't in the repository yet, simulating a same-batch cold start
        final var connector = domainBuilder.clprConnector().get();

        // when
        clprConnectorService.notify(connector);

        // then
        assertThat(clprConnectorService.lookup(connector.getChannelId(), connector.getConnectorId()))
                .isEqualTo(connector.getCommitment());
        verifyNoInteractions(clprConnectorRepository);
    }

    @Test
    void notifyWithoutConnectorIdIsNoOp() {
        // given -- a PENDING connector from ClprRegisterConnector, before ClprCompleteConnector reveals connectorId
        final var connector = domainBuilder
                .clprConnector()
                .customize(c -> c.connectorId(null))
                .get();

        // when
        clprConnectorService.notify(connector);

        // then
        verifyNoInteractions(clprConnectorRepository);
    }
}
