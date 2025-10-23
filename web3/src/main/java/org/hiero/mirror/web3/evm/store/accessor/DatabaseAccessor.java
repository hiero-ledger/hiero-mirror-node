// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store.accessor;

import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.springframework.core.ResolvableType;

/** A database accessor to get some domain type V with primary key K from the database.
 * <p>
 * _Must_ be implemented by a type with _no_ generic parameters extending ... from this type.  This is so that
 * it can figure out the generic parameters via `ResolvableType`.
 **/
public abstract class DatabaseAccessor<K, V> {
    @SuppressWarnings("unchecked")
    protected DatabaseAccessor() {

        // Capture type parameter classes from impl class
        final var myKlass = getClass();
        final var implType = ResolvableType.forClass(DatabaseAccessor.class, myKlass);
        final var genericParameters = implType.getGenerics();
        klassKey = (Class<K>) genericParameters[0].toClass();
        klassValue = (Class<V>) genericParameters[1].toClass();
    }

    // Given address return an account record from the DB
    @NonNull
    public abstract Optional<V> get(@NonNull final K key, final Optional<Long> timestamp);

    @NonNull
    public Class<K> getKeyClass() {
        return klassKey;
    }

    @NonNull
    public Class<V> getValueClass() {
        return klassValue;
    }

    private final Class<K> klassKey;
    private final Class<V> klassValue;
}
