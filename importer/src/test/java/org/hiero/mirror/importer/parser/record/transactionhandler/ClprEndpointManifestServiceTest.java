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
import org.hiero.mirror.importer.repository.ClprEndpointManifestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.caffeine.CaffeineCacheManager;

@ExtendWith(MockitoExtension.class)
final class ClprEndpointManifestServiceTest {

    private final DomainBuilder domainBuilder = new DomainBuilder();

    @Mock
    private ClprEndpointManifestRepository clprEndpointManifestRepository;

    private ClprEndpointManifestService clprEndpointManifestService;

    @BeforeEach
    void setup() {
        final var cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(Set.of(CACHE_NAME));
        clprEndpointManifestService = new ClprEndpointManifestService(cacheManager, clprEndpointManifestRepository);
    }

    @Test
    void lookupCacheMiss() {
        // given
        final var manifest = domainBuilder.clprEndpointManifest().get();
        when(clprEndpointManifestRepository.findLatest()).thenReturn(Optional.of(manifest));

        // when, then -- a second lookup hits the cache, not the repository again
        assertThat(clprEndpointManifestService.lookup()).isEqualTo(manifest);
        assertThat(clprEndpointManifestService.lookup()).isEqualTo(manifest);
        verify(clprEndpointManifestRepository).findLatest();
    }

    @Test
    void lookupCacheMissNotFound() {
        // given
        when(clprEndpointManifestRepository.findLatest()).thenReturn(Optional.empty());

        // when, then -- the miss itself is cached, so a second lookup still doesn't hit the repository again
        assertThat(clprEndpointManifestService.lookup()).isNull();
        assertThat(clprEndpointManifestService.lookup()).isNull();
        verify(clprEndpointManifestRepository).findLatest();
    }

    @Test
    void lookupLoaderExceptionReturnsNullInsteadOfPropagating() {
        // given
        when(clprEndpointManifestRepository.findLatest()).thenThrow(new RuntimeException("DB error"));

        // when, then
        assertThat(clprEndpointManifestService.lookup()).isNull();
    }

    @Test
    void notifyWarmsCacheAheadOfOwnDbWrite() {
        // given -- the manifest isn't in the repository yet, simulating a same-batch cold start
        final var manifest = domainBuilder.clprEndpointManifest().get();

        // when
        clprEndpointManifestService.notify(manifest);

        // then
        assertThat(clprEndpointManifestService.lookup()).isEqualTo(manifest);
        verifyNoInteractions(clprEndpointManifestRepository);
    }
}
