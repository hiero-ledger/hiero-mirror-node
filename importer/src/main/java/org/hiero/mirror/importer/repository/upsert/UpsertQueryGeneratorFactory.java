// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository.upsert;

import jakarta.inject.Named;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;

@CustomLog
@Named
@RequiredArgsConstructor
public class UpsertQueryGeneratorFactory {

    private final EntityMetadataRegistry entityMetadataRegistry;
    private final Collection<UpsertQueryGenerator> existingGenerators;
    private final Map<Class<?>, UpsertQueryGenerator> upsertQueryGenerators = new ConcurrentHashMap<>();

    public UpsertQueryGenerator get(Class<?> domainClass) {
        return upsertQueryGenerators.computeIfAbsent(domainClass, this::findOrCreate);
    }

    /**
     * This method relies on the convention that the domain class and its associated UpsertQueryGenerator have the same
     * prefix. Otherwise, it falls back to creating a generic upsert query generator.
     */
    private UpsertQueryGenerator findOrCreate(Class<?> domainClass) {
        String className = domainClass.getSimpleName() + UpsertQueryGenerator.class.getSimpleName();
        return existingGenerators.stream()
                .filter(u -> u.getClass().getSimpleName().equals(className))
                .findFirst()
                .orElseGet(() -> create(domainClass));
    }

    private UpsertQueryGenerator create(Class<?> domainClass) {
        EntityMetadata entityMetadata = entityMetadataRegistry.lookup(domainClass);
        return new GenericUpsertQueryGenerator(entityMetadata);
    }
}
