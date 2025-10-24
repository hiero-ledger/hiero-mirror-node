// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.core;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableQueueState;
import com.swirlds.state.spi.ReadableSingletonState;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MapReadableStatesTest {

    private MapReadableStates states;

    @Mock
    private ReadableKVState<String, String> kvStateMock;

    @Mock
    private ReadableSingletonState<String> singletonStateMock;

    @Mock
    private ReadableQueueState<String> queueStateMock;

    private static final int UNKNOW_STATE_ID = 0;
    private static final int KV_STATE_ID = 1;
    private static final int SINGLETON_STATE_ID = 2;
    private static final int QUEUE_STATE_ID = 3;

    @BeforeEach
    void setup() {
        states = new MapReadableStates(Map.of(
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
    void testEqualsDifferentType() {
        assertThat(states).isNotEqualTo("someString");
    }

    @Test
    void testEqualsWithNull() {
        assertThat(states).isNotEqualTo(null);
    }

    @Test
    void testEqualsSameValues() {
        MapReadableStates other = new MapReadableStates(Map.of(
                KV_STATE_ID, kvStateMock, SINGLETON_STATE_ID, singletonStateMock, QUEUE_STATE_ID, queueStateMock));
        assertThat(states).isEqualTo(other);
    }

    @Test
    void testEqualsDifferentValues() {
        MapReadableStates other = new MapReadableStates(Map.of(KV_STATE_ID, kvStateMock));
        assertThat(states).isNotEqualTo(other);
    }

    @Test
    void testHashCode() {
        MapReadableStates other = new MapReadableStates(Map.of(
                KV_STATE_ID, kvStateMock, SINGLETON_STATE_ID, singletonStateMock, QUEUE_STATE_ID, queueStateMock));
        assertThat(states).hasSameHashCodeAs(other);
    }
}
