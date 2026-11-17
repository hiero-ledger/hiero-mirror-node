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
import org.hiero.mirror.importer.repository.ClprEndpointManifestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.transaction.support.TransactionTemplate;

@RequiredArgsConstructor
class ClprEndpointManifestServiceTest extends ImporterIntegrationTest {

    // Caching is applied via the Spring proxy around this bean, so lookup/notify must be called through the
    // injected service -- not a bare `new ClprEndpointManifestService(...)` -- for @Cacheable/@CachePut to take
    // effect.
    private final ClprEndpointManifestService clprEndpointManifestService;

    private final ClprEndpointManifestRepository clprEndpointManifestRepository;
    private final TransactionTemplate transactionTemplate;

    @Test
    void lookupCacheHit() {
        // given
        final var manifest = domainBuilder.clprEndpointManifest().persist();

        // when, then
        assertThat(clprEndpointManifestService.lookup()).isEqualTo(manifest);

        // then -- a second lookup after the row is gone can only succeed via the cache, not the repository
        clprEndpointManifestRepository.deleteAll();
        assertThat(clprEndpointManifestService.lookup()).isEqualTo(manifest);
    }

    @Test
    void lookupCacheMissNotFound() {
        // when, then
        assertThat(clprEndpointManifestService.lookup()).isNull();

        // then -- the miss itself is cached, so a row persisted afterward still isn't found by a second lookup
        domainBuilder.clprEndpointManifest().persist();
        assertThat(clprEndpointManifestService.lookup()).isNull();
    }

    @Test
    void lookupPropagatesRepositoryException() {
        // given -- unlike the old manual cache.get(key, Callable), @Cacheable doesn't swallow a loader exception
        final var repository = mock(ClprEndpointManifestRepository.class);
        final var cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(Set.of(CACHE_NAME));
        final var service = new ClprEndpointManifestService(cacheManager, repository);
        when(repository.findLatest()).thenThrow(new RuntimeException("DB error"));

        // when, then
        assertThatThrownBy(service::lookup).isInstanceOf(RuntimeException.class).hasMessage("DB error");
    }

    @Test
    void notifyWarmsCacheAheadOfOwnDbWrite() {
        // given -- the manifest isn't in the repository yet, simulating a same-batch cold start
        final var manifest = domainBuilder.clprEndpointManifest().get();

        // when
        clprEndpointManifestService.notify(manifest);

        // then
        assertThat(clprEndpointManifestService.lookup()).isEqualTo(manifest);
    }

    @Test
    void notifyEvictsStagedValueWhenTransactionRollsBack() {
        // given -- notify called from within the enclosing parse transaction, which then fails to commit
        final var manifest = domainBuilder.clprEndpointManifest().get();
        transactionTemplate.executeWithoutResult(status -> {
            clprEndpointManifestService.notify(manifest);
            status.setRollbackOnly();
        });

        // then -- the never-committed value is gone, so the next lookup falls through to the repository again
        final var committed = domainBuilder.clprEndpointManifest().persist();
        assertThat(clprEndpointManifestService.lookup()).isEqualTo(committed);
    }

    @Test
    void notifyKeepsStagedValueWhenTransactionCommits() {
        // given -- notify called from within the enclosing parse transaction, which commits successfully
        final var manifest = domainBuilder.clprEndpointManifest().get();
        transactionTemplate.executeWithoutResult(status -> clprEndpointManifestService.notify(manifest));

        // then -- the committed value stays cached; the repository stays empty, so a value could only come from
        // the cache
        assertThat(clprEndpointManifestService.lookup()).isEqualTo(manifest);
    }
}
