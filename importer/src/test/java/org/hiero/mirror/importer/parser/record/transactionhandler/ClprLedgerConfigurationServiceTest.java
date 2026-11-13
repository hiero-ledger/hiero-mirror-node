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
import org.hiero.mirror.importer.repository.ClprLedgerConfigurationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.caffeine.CaffeineCacheManager;

@ExtendWith(MockitoExtension.class)
final class ClprLedgerConfigurationServiceTest {

    private final DomainBuilder domainBuilder = new DomainBuilder();

    @Mock
    private ClprLedgerConfigurationRepository clprLedgerConfigurationRepository;

    private ClprLedgerConfigurationService clprLedgerConfigurationService;

    @BeforeEach
    void setup() {
        final var cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(Set.of(CACHE_NAME));
        clprLedgerConfigurationService =
                new ClprLedgerConfigurationService(cacheManager, clprLedgerConfigurationRepository);
    }

    @Test
    void lookupCacheMiss() {
        // given
        final var configuration = domainBuilder.clprLedgerConfiguration().get();
        when(clprLedgerConfigurationRepository.findLatest()).thenReturn(Optional.of(configuration));

        // when, then -- a second lookup hits the cache, not the repository again
        assertThat(clprLedgerConfigurationService.lookup()).isEqualTo(configuration);
        assertThat(clprLedgerConfigurationService.lookup()).isEqualTo(configuration);
        verify(clprLedgerConfigurationRepository).findLatest();
    }

    @Test
    void lookupCacheMissNotFound() {
        // given
        when(clprLedgerConfigurationRepository.findLatest()).thenReturn(Optional.empty());

        // when, then -- the miss itself is cached, so a second lookup still doesn't hit the repository again
        assertThat(clprLedgerConfigurationService.lookup()).isNull();
        assertThat(clprLedgerConfigurationService.lookup()).isNull();
        verify(clprLedgerConfigurationRepository).findLatest();
    }

    @Test
    void lookupLoaderExceptionReturnsNullInsteadOfPropagating() {
        // given
        when(clprLedgerConfigurationRepository.findLatest()).thenThrow(new RuntimeException("DB error"));

        // when, then
        assertThat(clprLedgerConfigurationService.lookup()).isNull();
    }

    @Test
    void notifyWarmsCacheAheadOfOwnDbWrite() {
        // given -- the configuration isn't in the repository yet, simulating a same-batch cold start
        final var configuration = domainBuilder.clprLedgerConfiguration().get();

        // when
        clprLedgerConfigurationService.notify(configuration);

        // then
        assertThat(clprLedgerConfigurationService.lookup()).isEqualTo(configuration);
        verifyNoInteractions(clprLedgerConfigurationRepository);
    }
}
