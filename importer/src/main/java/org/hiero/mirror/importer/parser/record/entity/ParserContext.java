// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity;

import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.NonNull;

/**
 * Stores the domain objects parsed from the stream files before persisting to the database.
 */
@Named
public class ParserContext {

    private final Map<Class<?>, DomainContext<?>> state = new ConcurrentSkipListMap<>(new DomainClassComparator());

    public <T> void add(@NonNull T object) {
        add(object, null);
    }

    public <T> void add(@NonNull T object, Object key) {
        var domainContext = getDomainContext(object);
        domainContext.getInserts().add(object);

        if (key != null) {
            domainContext.getState().put(key, object);
        }
    }

    public <T> void addAll(@NonNull Collection<T> objects) {
        if (!objects.isEmpty()) {
            var first = objects.iterator().next();
            var domainContext = getDomainContext(first);
            domainContext.getInserts().addAll(objects);
        }
    }

    public void clear() {
        state.clear();
    }

    public void forEach(@NonNull Consumer<Collection<?>> sink) {
        state.forEach((c, v) -> sink.accept(v.getInserts()));
    }

    public <T> T get(@NonNull Class<T> domainClass, @NonNull Object key) {
        var domainContext = getDomainContext(domainClass);
        return domainContext.getState().get(key);
    }

    public <T> Collection<T> get(@NonNull Class<T> domainClass) {
        var domainContext = getDomainContext(domainClass);
        return Collections.unmodifiableList(domainContext.getInserts());
    }

    public <T> void merge(@NonNull Object key, @NonNull T value, @NonNull BinaryOperator<T> mergeFunction) {
        var domainContext = getDomainContext(value);
        var merged = domainContext.getState().merge(key, value, mergeFunction);

        if (merged == value) {
            domainContext.getInserts().add(value);
        }
    }

    public void remove(@NonNull Class<?> domainClass) {
        var domainContext = getDomainContext(domainClass);
        domainContext.clear();
    }

    @SuppressWarnings("unchecked")
    private <T> DomainContext<T> getDomainContext(T object) {
        var domainClass = (Class<T>) object.getClass();
        return getDomainContext(domainClass);
    }

    @SuppressWarnings("unchecked")
    private <T> DomainContext<T> getDomainContext(Class<T> domainClass) {
        return (DomainContext<T>) state.computeIfAbsent(domainClass, c -> new DomainContext<>());
    }

    private class DomainContext<T> {

        @Getter
        private final List<T> inserts = new ArrayList<>();

        @Getter(lazy = true)
        private final Map<Object, T> state = new HashMap<>();

        void clear() {
            getInserts().clear();
            getState().clear();
        }
    }
}
