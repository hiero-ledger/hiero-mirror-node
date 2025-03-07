// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state.core;

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

    private static final String KV_STATE_KEY = "kvState";
    private static final String SINGLETON_KEY = "singleton";
    private static final String QUEUE_KEY = "queue";

    @BeforeEach
    void setup() {
        states = new MapReadableStates(
                Map.of(KV_STATE_KEY, kvStateMock, SINGLETON_KEY, singletonStateMock, QUEUE_KEY, queueStateMock));
    }

    @Test
    void testGetState() {
        assertThat(states.get(KV_STATE_KEY)).isEqualTo(kvStateMock);
    }

    @Test
    void testGetStateNotFound() {
        assertThatThrownBy(() -> states.get("unknown")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testGetStateNotCorrectType() {
        assertThatThrownBy(() -> states.get(SINGLETON_KEY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testGetSingletonState() {
        assertThat(states.getSingleton(SINGLETON_KEY)).isEqualTo(singletonStateMock);
    }

    @Test
    void testGetSingletonStateNotFound() {
        assertThatThrownBy(() -> states.getSingleton("unknown")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testGetSingletonStateNotCorrectType() {
        assertThatThrownBy(() -> states.getSingleton(QUEUE_KEY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testGetQueueState() {
        assertThat(states.getQueue(QUEUE_KEY)).isEqualTo(queueStateMock);
    }

    @Test
    void testGetQueueStateNotFound() {
        assertThatThrownBy(() -> states.getQueue("unknown")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testGetQueueStateNotCorrectType() {
        assertThatThrownBy(() -> states.getQueue(KV_STATE_KEY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testContains() {
        assertThat(states.contains(KV_STATE_KEY)).isTrue();
        assertThat(states.contains(SINGLETON_KEY)).isTrue();
        assertThat(states.contains(QUEUE_KEY)).isTrue();
        assertThat(states.contains("unknown")).isFalse();
    }

    @Test
    void testStateKeysReturnsCorrectSet() {
        assertThat(states.stateKeys()).isEqualTo(Set.of(KV_STATE_KEY, SINGLETON_KEY, QUEUE_KEY));
    }

    @Test
    void testStateKeysReturnsUnmodifiableSet() {
        Set<String> keys = states.stateKeys();
        assertThatThrownBy(() -> keys.add("newKey")).isInstanceOf(UnsupportedOperationException.class);
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
        MapReadableStates other = new MapReadableStates(
                Map.of(KV_STATE_KEY, kvStateMock, SINGLETON_KEY, singletonStateMock, QUEUE_KEY, queueStateMock));
        assertThat(states).isEqualTo(other);
    }

    @Test
    void testEqualsDifferentValues() {
        MapReadableStates other = new MapReadableStates(Map.of(KV_STATE_KEY, kvStateMock));
        assertThat(states).isNotEqualTo(other);
    }

    @Test
    void testHashCode() {
        MapReadableStates other = new MapReadableStates(
                Map.of(KV_STATE_KEY, kvStateMock, SINGLETON_KEY, singletonStateMock, QUEUE_KEY, queueStateMock));
        assertThat(states).hasSameHashCodeAs(other);
    }
}
