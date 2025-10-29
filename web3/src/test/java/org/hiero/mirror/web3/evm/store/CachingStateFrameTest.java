// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Optional;
import org.hiero.mirror.web3.evm.store.CachingStateFrame.CacheAccessIncorrectTypeException;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

class CachingStateFrameTest {

    // Test construction produces a correct instance

    @Test
    void constructWithNoUpstream() {

        final var sut = new BareCachingStateFrame<Integer>(Optional.empty(), Integer.class);
        assertThat(sut.getUpstream()).isEmpty();
        assertThat(sut.height()).isEqualTo(1);
    }

    @Test
    void constructWithSingleUpstream() {
        final var us0 = new BareCachingStateFrame<Integer>(Optional.empty(), Integer.class);
        final var sut = new BareCachingStateFrame<>(Optional.of(us0), Integer.class);

        assertThat(sut.getUpstream()).contains(us0);
        assertThat(sut.height()).isEqualTo(2);
    }

    @Test
    void constructWithTwoFramesUpstream() {
        final var us0 = new BareCachingStateFrame<Integer>(Optional.empty(), Integer.class);
        final var us1 = new BareCachingStateFrame<>(Optional.of(us0), Integer.class);
        final var sut = new BareCachingStateFrame<>(Optional.of(us1), Integer.class);

        assertThat(sut.getUpstream()).contains(us1);
        assertThat(sut.height()).isEqualTo(3);
    }

    @Test
    void constructWithNoValueTypes() {
        assertThatIllegalArgumentException().isThrownBy(() -> new BareCachingStateFrame<Integer>(Optional.empty()));
    }

    @Test
    void constructWithOneTypeOfValues() {
        final var sut = new BareCachingStateFrame<Integer>(Optional.empty(), Integer.class);

        assertThat(sut.getAccessor(Integer.class)).isNotNull();
        assertThatExceptionOfType(CacheAccessIncorrectTypeException.class)
                .isThrownBy(() -> sut.getAccessor(String.class));
        assertThat(sut.getInternalCaches())
                .isNotNull()
                .hasSize(1)
                .containsOnlyKeys(Integer.class)
                .doesNotContainValue(null);
    }

    @Test
    void constructWithTwoTypesOfValues() {
        final var sut = new BareCachingStateFrame<Integer>(Optional.empty(), Integer.class, Character.class);

        assertThat(sut.getAccessor(Integer.class)).isNotNull();
        assertThat(sut.getAccessor(Character.class)).isNotNull();
        assertThatExceptionOfType(CacheAccessIncorrectTypeException.class)
                .isThrownBy(() -> sut.getAccessor(String.class));
        assertThat(sut.getInternalCaches())
                .isNotNull()
                .hasSize(2)
                .containsOnlyKeys(Integer.class, Character.class)
                .doesNotContainValue(null);
    }

    @Test
    void accessorForSingleType() {
        final var actual = new StringBuilder(500);
        final var sut = new RecordingCachingStateFrame<Integer>(actual, Optional.empty(), Integer.class);

        final var keys = List.of(2, 4, 6, 8);
        final var values = List.of(10, 20, 30, 40);
        assertThat(values).hasSize(keys.size());

        final var expected = new StringBuilder(500);
        final var sutAccessor = sut.getAccessor(Integer.class);
        for (int i = 0; i < keys.size(); i++) {
            final var key = keys.get(i);
            final var value = values.get(i);

            expected.append("S-I-%d-%d;D-I-%d;G-I-%d;".formatted(key, value, key, key));

            sutAccessor.set(key, value);
            sutAccessor.delete(key);
            sutAccessor.get(key);
        }

        assertThat(actual.toString()).hasToString(expected.toString());
    }

    // Test accessors - not semantics of caching, but just that they pass through correctly to lower-level methods for
    // cache access

    @Test
    void accessorsForTwoTypes() {
        final var actual = new StringBuilder(500);
        final var sut =
                new RecordingCachingStateFrame<Integer>(actual, Optional.empty(), Integer.class, Character.class);

        final var keys = List.of(2, 4, 6, 8);
        final var valuesI = List.of(10, 20, 30, 40);
        final var valuesC = List.of('A', 'b', 'X', 'y');
        assertThat(valuesI).hasSize(keys.size());
        assertThat(valuesC).hasSize(keys.size());

        final var expected = new StringBuilder(500);
        final var sutAccessorI = sut.getAccessor(Integer.class);
        final var sutAccessorC = sut.getAccessor(Character.class);
        for (int i = 0; i < keys.size(); i++) {
            final var key = keys.get(i);
            final var valueI = valuesI.get(i);
            final var valueC = valuesC.get(i);

            expected.append("S-I-%d-%d;S-C-%d-%c;D-I-%d;D-C-%d;G-I-%d;G-C-%d;"
                    .formatted(key, valueI, key, valueC, key, key, key, key));

            sutAccessorI.set(key, valueI);
            sutAccessorC.set(key, valueC);
            sutAccessorI.delete(key);
            sutAccessorC.delete(key);
            sutAccessorI.get(key);
            sutAccessorC.get(key);
        }

        assertThat(actual.toString()).hasToString(expected.toString());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setFailsOnBadValueType() {
        final var sut = new BareCachingStateFrame<Integer>(Optional.empty(), Character.class);
        final var sutAccessorAsFetched = sut.getAccessor(Character.class);
        // N.B.: Really have to go out of your way - via unchecked casting - to violate the type safety
        final var sutAccessor =
                (CachingStateFrame.Accessor<Integer, String>) (CachingStateFrame.Accessor) sutAccessorAsFetched;

        assertThatExceptionOfType(CacheAccessIncorrectTypeException.class)
                .isThrownBy(() -> sutAccessor.set(7, "foobar"));
    }

    @Test
    void getFailsOnBadValueType() {
        final var sut = new BareCachingStateFrame<Integer>(Optional.empty(), Character.class) {
            @NonNull
            @Override
            protected Optional<Object> getValue(
                    @NonNull final Class<?> klass,
                    @NonNull final UpdatableReferenceCache<Integer> cache,
                    @NonNull final Integer key) {
                return Optional.of("foobar" /* returning String, not Character */);
            }
        };
        final var sutAccessor = sut.getAccessor(Character.class);

        assertThatExceptionOfType(CacheAccessIncorrectTypeException.class).isThrownBy(() -> sutAccessor.get(7));
    }

    // Accessors detect type errors

    @Test
    void commitDoesNothingIfNoUpstreamFrames() {
        final var actual = new StringBuilder(500);
        final var sut = new RecordingCachingStateFrame<Integer>(actual, Optional.empty(), Integer.class);

        sut.commit();

        assertThat(actual).isEmpty();
    }

    // Test commit properly updates upstream frames

    @Test
    void commitCallsUpstreamUpdate() {
        final var actual = new StringBuilder(500);
        final var us0 = new RecordingCachingStateFrame<Integer>(actual, Optional.empty(), Integer.class);
        final var sut = new BareCachingStateFrame<>(Optional.of(us0), Integer.class);

        sut.commit();

        assertThat(actual).hasToString("U;");
    }

    /** A no-additional-behavior-at-all subclass of the abstract `CachingStateFrame`, suitable for constructor tests. */
    static class BareCachingStateFrame<K> extends CachingStateFrame<K> {

        public BareCachingStateFrame(
                @NonNull final Optional<CachingStateFrame<K>> upstreamFrame,
                @NonNull final Class<?>... klassesToCache) {
            super(upstreamFrame, klassesToCache);
        }

        @Override
        public void updatesFromDownstream(@NonNull final CachingStateFrame<K> childFrame) {
            throw new UnsupportedOperationException("Missing implementation");
        }

        @NonNull
        @Override
        protected Optional<Object> getValue(
                @NonNull final Class<?> klass, @NonNull final UpdatableReferenceCache<K> cache, @NonNull final K key) {
            return Optional.empty();
        }

        @Override
        protected void setValue(
                @NonNull final Class<?> klass,
                @NonNull final UpdatableReferenceCache<K> cache,
                @NonNull final K key,
                @NonNull final Object value) {
            // No op
        }

        @Override
        protected void deleteValue(
                @NonNull final Class<?> klass, @NonNull final UpdatableReferenceCache<K> cache, @NonNull final K key) {
            // No op
        }
    }

    /** A spying `CachingStateFrame` that records accesses to the methods underlying value accessors */
    static class RecordingCachingStateFrame<K> extends CachingStateFrame<K> {

        @NonNull
        final StringBuilder spy;

        public RecordingCachingStateFrame(
                @NonNull final StringBuilder spy,
                @NonNull Optional<CachingStateFrame<K>> upstreamFrame,
                @NonNull final Class<?>... klassesToCache) {
            super(upstreamFrame, klassesToCache);
            this.spy = spy;
        }

        @Override
        public void updatesFromDownstream(@NonNull final CachingStateFrame<K> childFrame) {
            spy.append("U;");
        }

        @NonNull
        @Override
        protected Optional<Object> getValue(
                @NonNull final Class<?> klass, @NonNull final UpdatableReferenceCache<K> cache, @NonNull final K key) {
            spy.append("%s-%s-%s;".formatted("G", klass.getSimpleName().charAt(0), key.toString()));
            return Optional.empty();
        }

        @Override
        protected void setValue(
                @NonNull final Class<?> klass,
                @NonNull final UpdatableReferenceCache<K> cache,
                @NonNull final K key,
                @NonNull final Object value) {
            spy.append(
                    "%s-%s-%s-%s;".formatted("S", klass.getSimpleName().charAt(0), key.toString(), value.toString()));
        }

        @Override
        protected void deleteValue(
                @NonNull final Class<?> klass, @NonNull final UpdatableReferenceCache<K> cache, @NonNull final K key) {
            spy.append("%s-%s-%s;".formatted("D", klass.getSimpleName().charAt(0), key.toString()));
        }
    }
}
