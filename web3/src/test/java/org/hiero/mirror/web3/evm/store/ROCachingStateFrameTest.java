// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.hiero.mirror.web3.evm.store.impl.UpdatableReferenceCacheLineState.ValueState.INVALID;
import static org.hiero.mirror.web3.evm.store.impl.UpdatableReferenceCacheLineState.ValueState.NOT_YET_FETCHED;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.hiero.mirror.web3.evm.store.impl.UpdatableReferenceCacheLineState.Entry;
import org.hiero.mirror.web3.evm.store.impl.UpdatableReferenceCacheLineState.ValueState;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ROCachingStateFrameTest {

    @Mock
    UpdatableReferenceCache<Integer> mockCache;

    @Test
    void getValidOfNotYetFetchedGoesUpstreamAndItIsMissing() {
        final Integer k = 555;

        final var upstreamFrame = new CannedCachingStateFrame(Optional.empty());
        when(mockCache.get(k)).thenReturn(new Entry(NOT_YET_FETCHED, null));
        final var sut = new ROCachingStateFrame<>(Optional.of(upstreamFrame), Character.class);
        final var actual = sut.getValue(Character.class, mockCache, k);

        assertThat(actual).isEmpty();
        verify(mockCache, times(1)).fill(k, null);
    }

    @Test
    void getValidOfNotYetFetchedGoesUpstreamAndItIsPresent() {
        final Integer k = 555;
        final Character v = 'C';

        final var upstreamFrame = new CannedCachingStateFrame(Optional.of(v));
        when(mockCache.get(k)).thenReturn(new Entry(NOT_YET_FETCHED, null));
        final var sut = new ROCachingStateFrame<>(Optional.of(upstreamFrame), Character.class);
        final var actual = sut.getValue(Character.class, mockCache, k);

        assertThat(actual).contains(v);
        verify(mockCache, times(1)).fill(k, v);
    }

    @ParameterizedTest
    @EnumSource(
            value = ValueState.class,
            names = {"PRESENT", "UPDATED"})
    void getValidOfPresentOrUpdatedEntryReturnsValue(ValueState state) {
        final Integer k = 555;
        final Character v = 'C';
        when(mockCache.get(k)).thenReturn(new Entry(state, v));
        final var sut = new ROCachingStateFrame<Integer>(Optional.empty(), Character.class);
        final var actual = sut.getValue(Character.class, mockCache, k);
        assertThat(actual).contains(v);
    }

    @ParameterizedTest
    @EnumSource(
            value = ValueState.class,
            names = {"MISSING", "DELETED"})
    void getValidOfMissingOrDeletedEntryReturnsEmpty(ValueState state) {
        final Integer k = 555;
        when(mockCache.get(k)).thenReturn(new Entry(state, null));
        final var sut = new ROCachingStateFrame<Integer>(Optional.empty(), Character.class);
        final var actual = sut.getValue(Character.class, mockCache, k);
        assertThat(actual).isEmpty();
    }

    @Test
    void getValueOfInvalidEntryThrows() {
        final Integer k = 555;
        when(mockCache.get(k)).thenReturn(new Entry(INVALID, null));
        final var sut = new ROCachingStateFrame<Integer>(Optional.empty(), Character.class);
        assertThatIllegalArgumentException().isThrownBy(() -> sut.getValue(Character.class, mockCache, k));
    }

    @Test
    void setValueIsNotAllowed() {
        final var cache = new UpdatableReferenceCache<Integer>();
        final var sut = new ROCachingStateFrame<Integer>(Optional.empty(), Character.class);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> sut.setValue(Character.class, cache, 1, 'C'));
    }

    @Test
    void deleteValueIsNotAllowed() {
        final var cache = new UpdatableReferenceCache<Integer>();
        final var sut = new ROCachingStateFrame<Integer>(Optional.empty(), Character.class);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> sut.deleteValue(Character.class, cache, 1));
    }

    @Test
    void updatesFromDownstreamIsNotAllowed() {
        final var sut = new ROCachingStateFrame<Integer>(Optional.empty(), Character.class);
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> sut.updatesFromDownstream(sut));
    }

    static class CannedCachingStateFrame extends CachingStateFrame<Integer> {

        final Optional<Character> cannedValue;

        public CannedCachingStateFrame(@NonNull final Optional<Character> cannedValue) {
            super(Optional.empty(), Character.class);
            this.cannedValue = cannedValue;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        @NonNull
        @Override
        public <V> Accessor<Integer, V> getAccessor(@NonNull Class<V> klass) {
            return new Accessor<Integer, V>() {
                @Override
                public Optional<V> get(@NonNull final Integer key) {
                    return (Optional<V>) cannedValue;
                }

                @Override
                public void set(@NonNull final Integer key, @NonNull final V value) {
                    /* not needed */
                }

                @Override
                public void delete(@NonNull final Integer key) {
                    /* not needed */
                }
            };
        }

        @Override
        public void updatesFromDownstream(@NonNull final CachingStateFrame<Integer> childFrame) {
            /* not needed */
        }

        @NonNull
        @Override
        protected Optional<Object> getValue(
                @NonNull final Class<?> klass,
                @NonNull final UpdatableReferenceCache<Integer> cache,
                @NonNull final Integer key) {
            /* not needed */
            return Optional.empty();
        }

        @Override
        protected void setValue(
                @NonNull final Class<?> klass,
                @NonNull final UpdatableReferenceCache<Integer> cache,
                @NonNull final Integer key,
                @NonNull final Object value) {
            /* not needed */
        }

        @Override
        protected void deleteValue(
                @NonNull final Class<?> klass,
                @NonNull final UpdatableReferenceCache<Integer> cache,
                @NonNull final Integer key) {
            /* not needed */
        }
    }
}
