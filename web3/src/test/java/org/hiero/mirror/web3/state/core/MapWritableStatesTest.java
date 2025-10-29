// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.core;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.swirlds.state.spi.WritableKVStateBase;
import com.swirlds.state.spi.WritableQueueStateBase;
import com.swirlds.state.spi.WritableSingletonStateBase;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MapWritableStatesTest {

    private MapWritableStates states;

    @Mock
    private WritableKVStateBase<String, String> kvStateMock;

    @Mock
    private WritableSingletonStateBase<String> singletonStateMock;

    @Mock
    private WritableQueueStateBase<String> queueStateMock;

    private static final int UNKNOW_STATE_ID = 0;
    private static final int KV_STATE_ID = 1;
    private static final int SINGLETON_STATE_ID = 2;
    private static final int QUEUE_STATE_ID = 3;

    @BeforeEach
    void setup() {
        states = new MapWritableStates(Map.of(
                KV_STATE_ID, kvStateMock, SINGLETON_STATE_ID, singletonStateMock, QUEUE_STATE_ID, queueStateMock));
    }

    @Test
    void testGetState() {
        assertThat(states.get(KV_STATE_ID)).isEqualTo(kvStateMock);
    }

    @Test
    void testGetStateNotFound() {
        assertThatThrownBy(() -> states.get(UNKNOW_STATE_ID)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testGetStateNotCorrectType() {
        assertThatThrownBy(() -> states.get(SINGLETON_STATE_ID)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testGetSingletonState() {
        assertThat(states.getSingleton(SINGLETON_STATE_ID)).isEqualTo(singletonStateMock);
    }

    @Test
    void testGetSingletonStateNotFound() {
        assertThatThrownBy(() -> states.getSingleton(UNKNOW_STATE_ID)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testGetSingletonStateNotCorrectType() {
        assertThatThrownBy(() -> states.getSingleton(QUEUE_STATE_ID)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testGetQueueState() {
        assertThat(states.getQueue(QUEUE_STATE_ID)).isEqualTo(queueStateMock);
    }

    @Test
    void testGetQueueStateNotFound() {
        assertThatThrownBy(() -> states.getQueue(UNKNOW_STATE_ID)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testGetQueueStateNotCorrectType() {
        assertThatThrownBy(() -> states.getQueue(KV_STATE_ID)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testContains() {
        assertThat(states.contains(KV_STATE_ID)).isTrue();
        assertThat(states.contains(SINGLETON_STATE_ID)).isTrue();
        assertThat(states.contains(QUEUE_STATE_ID)).isTrue();
        assertThat(states.contains(UNKNOW_STATE_ID)).isFalse();
    }

    @Test
    void testStateKeysReturnsCorrectSet() {
        assertThat(states.stateIds()).isEqualTo(Set.of(KV_STATE_ID, SINGLETON_STATE_ID, QUEUE_STATE_ID));
    }

    @Test
    void testStateKeysReturnsUnmodifiableSet() {
        Set<Integer> keys = states.stateIds();
        assertThatThrownBy(() -> keys.add(4)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testEqualsSameInstance() {
        assertThat(states).isEqualTo(states);
    }

    @Test
    void testEqualsDifferentClass() {
        assertThat(states).isNotEqualTo("other");
    }

    @Test
    void testEqualsWithNull() {
        assertThat(states).isNotEqualTo(null);
    }

    @Test
    void testHashCode() {
        MapWritableStates other = new MapWritableStates(Map.of(
                KV_STATE_ID, kvStateMock, SINGLETON_STATE_ID, singletonStateMock, QUEUE_STATE_ID, queueStateMock));
        assertThat(states).hasSameHashCodeAs(other);
    }

    @Test
    void testCommit() {
        final var state = new MapWritableStates(Map.of(
                KV_STATE_ID, kvStateMock, SINGLETON_STATE_ID, singletonStateMock, QUEUE_STATE_ID, queueStateMock));
        state.commit();
        verify(kvStateMock, times(1)).commit();
        verify(singletonStateMock, times(1)).commit();
        verify(queueStateMock, times(1)).commit();
    }

    @Test
    void testCommitWithListener() {
        final Runnable onCommit = mock(Runnable.class);
        final var state = new MapWritableStates(Map.of(KV_STATE_ID, kvStateMock), onCommit);
        state.commit();
        verify(kvStateMock, times(1)).commit();
        verify(onCommit, times(1)).run();
    }

    @Test
    void testCommitUnknownValue() {
        final var state = new MapWritableStates(Map.of(4, new Object()));
        assertThatThrownBy(state::commit).isInstanceOf(IllegalStateException.class);
    }
}
