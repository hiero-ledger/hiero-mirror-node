// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store;

import java.io.Serial;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.hiero.mirror.web3.evm.exception.EvmException;
import org.hiero.mirror.web3.evm.store.accessor.DatabaseAccessor;
import org.jspecify.annotations.NonNull;

/**
 * A CachingStateFrame that answers reads by getting entities from some other source - a database! - and disallows all
 * local updates/deletes.
 */
public class DatabaseBackedStateFrame<K> extends CachingStateFrame<K> {

    @NonNull
    final Map<Class<?>, DatabaseAccessor<K, ?>> databaseAccessors;

    final Optional<Long> timestamp;

    public DatabaseBackedStateFrame(
            @NonNull final List<DatabaseAccessor<K, ?>> accessors,
            @NonNull final Class<?>[] valueClasses,
            final Optional<Long> timestamp) {
        super(
                Optional.empty(),
                valueClasses); // superclass of this frame will create/hold useless UpdatableReferenceCaches
        databaseAccessors = accessors.stream().collect(Collectors.toMap(DatabaseAccessor::getValueClass, a -> a));
        this.timestamp = timestamp;
    }

    @Override
    @NonNull
    public Optional<Object> getValue(
            @NonNull final Class<?> klass, @NonNull final UpdatableReferenceCache<K> cache, @NonNull final K key) {
        final var databaseAccessor = databaseAccessors.get(klass);
        if (databaseAccessor == null) {
            throw new NullPointerException("no available accessor for given klass");
        }
        return databaseAccessor.get(key, timestamp).flatMap(o -> Optional.of(klass.cast(o)));
    }

    @Override
    public void setValue(
            @NonNull final Class<?> klass,
            @NonNull final UpdatableReferenceCache<K> cache,
            @NonNull final K key,
            @NonNull final Object value) {
        throw new UnsupportedOperationException("Cannot set a value in a read only database");
    }

    @Override
    public void deleteValue(
            @NonNull final Class<?> klass, @NonNull final UpdatableReferenceCache<K> cache, @NonNull final K key) {
        throw new UnsupportedOperationException("Cannot delete a value in a read only database");
    }

    @Override
    public void updatesFromDownstream(@NonNull final CachingStateFrame<K> childFrame) {
        throw new UnsupportedOperationException("Cannot commit to a read only database");
    }

    @Override
    public void commit() {
        throw new UnsupportedOperationException("Cannot commit to a read only database");
    }

    /** Signals that a type error occurred with the _value_ type */
    @SuppressWarnings("java:S110")
    public static class DatabaseAccessIncorrectKeyTypeException extends EvmException {

        @Serial
        private static final long serialVersionUID = 1163169205069277931L;

        public DatabaseAccessIncorrectKeyTypeException(@NonNull final String message) {
            super(message);
        }
    }
}
