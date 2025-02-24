// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.evm.store;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigInteger;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RWCachingStateFrameTest {

    @Mock
    UpdatableReferenceCache<Integer> mockCache;

    @Test
    void setValueSetsTheValue() {
        final Integer k = 555;
        final Character v = 'C';
        final var sut = new RWCachingStateFrame<Integer>(Optional.empty(), Character.class);
        sut.setValue(Character.class, mockCache, k, v);
        verify(mockCache, times(1)).update(k, v);
    }

    @Test
    void deleteValueDeletesTheValue() {
        final Integer k = 555;
        final var sut = new RWCachingStateFrame<Integer>(Optional.empty(), Character.class);
        sut.deleteValue(Character.class, mockCache, k);
        verify(mockCache, times(1)).delete(k);
    }

    @Test
    void updatesFromDownstreamRequiresSameClassesRegistered() {
        final var otherFrame1 = new BottomCachingStateFrame<Integer>(Optional.empty(), Character.class);
        final var otherFrame2 =
                new BottomCachingStateFrame<Integer>(Optional.empty(), Character.class, BigInteger.class);

        final var sut = new RWCachingStateFrame<Integer>(Optional.empty(), Character.class, String.class);

        assertThatIllegalStateException().isThrownBy(() -> sut.updatesFromDownstream(otherFrame1));
        assertThatIllegalStateException().isThrownBy(() -> sut.updatesFromDownstream(otherFrame2));
    }

    @SuppressWarnings("unchecked")
    @Test
    void updatesFromDownStreamCoalesceProperly() {

        enum CacheKind {
            THIS_C,
            THIS_S,
            DOWN_C,
            DOWN_S
        }

        final EnumMap<CacheKind, UpdatableReferenceCache<Integer>> caches = new EnumMap<>(CacheKind.class);

        for (final var ck : CacheKind.values()) {
            caches.put(ck, Mockito.mock(UpdatableReferenceCache.class));
        }

        final var downstreamFrame =
                Mockito.spy(new BottomCachingStateFrame<Integer>(Optional.empty(), Character.class, String.class));

        final var sutInternalCaches =
                Map.of(Character.class, caches.get(CacheKind.THIS_C), String.class, caches.get(CacheKind.THIS_S));
        final var downstreamInternalCaches =
                Map.of(Character.class, caches.get(CacheKind.DOWN_C), String.class, caches.get(CacheKind.DOWN_S));

        final var sut = Mockito.spy(new RWCachingStateFrame<Integer>(Optional.empty(), Character.class, String.class));

        doReturn(downstreamInternalCaches).when(downstreamFrame).getInternalCaches();
        doReturn(sutInternalCaches).when(sut).getInternalCaches();

        sut.updatesFromDownstream(downstreamFrame);

        verify(caches.get(CacheKind.THIS_C), times(1)).coalesceFrom(caches.get(CacheKind.DOWN_C));
        verify(caches.get(CacheKind.THIS_S), times(1)).coalesceFrom(caches.get(CacheKind.DOWN_S));
    }
}
