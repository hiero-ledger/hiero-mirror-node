// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.importer.config.CacheConfiguration.CACHE_NAME;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.repository.ClprLedgerConfigurationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.cache.caffeine.CaffeineCacheManager;

@RequiredArgsConstructor
class ClprLedgerConfigurationServiceTest extends ImporterIntegrationTest {

    // Caching is applied via the Spring proxy around this bean, so lookup/notify must be called through the
    // injected service -- not a bare `new ClprLedgerConfigurationService(...)` -- for @Cacheable/@CachePut to take
    // effect.
    private final ClprLedgerConfigurationService clprLedgerConfigurationService;

    private final ClprLedgerConfigurationRepository clprLedgerConfigurationRepository;

    @Test
    void lookupCacheHit() {
        // given
        final var configuration = domainBuilder.clprLedgerConfiguration().persist();

        // when, then
        assertThat(clprLedgerConfigurationService.lookup()).isEqualTo(configuration);

        // then -- a second lookup after the row is gone can only succeed via the cache, not the repository
        clprLedgerConfigurationRepository.deleteAll();
        assertThat(clprLedgerConfigurationService.lookup()).isEqualTo(configuration);
    }

    @Test
    void lookupCacheMissNotFound() {
        // when, then
        assertThat(clprLedgerConfigurationService.lookup()).isNull();

        // then -- the miss itself is cached, so a row persisted afterward still isn't found by a second lookup
        domainBuilder.clprLedgerConfiguration().persist();
        assertThat(clprLedgerConfigurationService.lookup()).isNull();
    }

    @Test
    void lookupPropagatesRepositoryException() {
        // given -- unlike the old manual cache.get(key, Callable), @Cacheable doesn't swallow a loader exception
        final var repository = mock(ClprLedgerConfigurationRepository.class);
        final var cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(Set.of(CACHE_NAME));
        final var service = new ClprLedgerConfigurationService(cacheManager, repository);
        when(repository.findLatest()).thenThrow(new RuntimeException("DB error"));

        // when, then
        assertThatThrownBy(service::lookup).isInstanceOf(RuntimeException.class).hasMessage("DB error");
    }

    @Test
    void notifyWarmsCacheAheadOfOwnDbWrite() {
        // given -- the configuration isn't in the repository yet, simulating a same-batch cold start
        final var configuration = domainBuilder.clprLedgerConfiguration().get();

        // when
        clprLedgerConfigurationService.notify(configuration);

        // then
        assertThat(clprLedgerConfigurationService.lookup()).isEqualTo(configuration);
    }

    @Test
    void handleParseFailureEvictsStagedValueFromFailedBatch() {
        // given -- notified by a batch starting at consensusTimestamp 100 that then failed to parse
        final var configuration = domainBuilder
                .clprLedgerConfiguration()
                .customize(c -> c.consensusTimestamp(150L))
                .get();
        clprLedgerConfigurationService.notify(configuration);

        // when -- the retry calls this with that same batch's starting timestamp
        clprLedgerConfigurationService.handleParseFailure(100L);

        // then -- the never-committed value is gone, so the next lookup falls through to the repository again
        final var committed = domainBuilder.clprLedgerConfiguration().persist();
        assertThat(clprLedgerConfigurationService.lookup()).isEqualTo(committed);
    }

    @Test
    void handleParseFailureKeepsValueFromAnEarlierCommittedBatch() {
        // given -- notified by a batch starting at consensusTimestamp 100 that committed successfully
        final var configuration = domainBuilder
                .clprLedgerConfiguration()
                .customize(c -> c.consensusTimestamp(150L))
                .get();
        clprLedgerConfigurationService.notify(configuration);

        // when -- a later, unrelated batch starting at 200 fails
        clprLedgerConfigurationService.handleParseFailure(200L);

        // then -- the earlier, already-committed value is untouched; the repository stays empty, so a value could
        // only come from the cache
        assertThat(clprLedgerConfigurationService.lookup()).isEqualTo(configuration);
    }
}
