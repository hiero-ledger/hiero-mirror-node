// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.graphql.cache;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import graphql.ExecutionInput;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.execution.preparsed.PreparsedDocumentProvider;
import jakarta.inject.Named;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Named
final class CachedPreparsedDocumentProvider implements PreparsedDocumentProvider {

    private final AsyncCache<String, PreparsedDocumentEntry> cache;

    CachedPreparsedDocumentProvider(CacheProperties properties) {
        cache = Caffeine.from(properties.getQuery()).buildAsync();
    }

    @Override
    public CompletableFuture<PreparsedDocumentEntry> getDocumentAsync(
            ExecutionInput executionInput, Function<ExecutionInput, PreparsedDocumentEntry> parseAndValidateFunction) {
        return cache.get(executionInput.getQuery(), key -> parseAndValidateFunction.apply(executionInput));
    }
}
