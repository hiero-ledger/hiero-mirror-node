// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings({"unchecked"})
@ExtendWith(MockitoExtension.class)
class ListWritableQueueStateTest {

    private Queue<Object> backingStore;

    @BeforeEach
    void setup() {
        backingStore = new ConcurrentLinkedDeque<>();
    }

    @Test
    void testAddToDatasource() {
        final var elem = new Object();
        final var queueState = new ListWritableQueueState<>("SERVICE_NAME", "KEY", backingStore);
        queueState.addToDataSource(elem);
        assertThat(backingStore).contains(elem);
    }

    @Test
    void testRemoveFromDatasource() {
        final var elem = new Object();
        backingStore.add(elem);
        final var queueState = new ListWritableQueueState<>("SERVICE_NAME", "KEY", backingStore);
        queueState.removeFromDataSource();
        assertThat(backingStore).isEmpty();
    }

    @Test
    void testIterateOnDataSource() {
        final var elem = new Object();
        backingStore.add(elem);
        final var iterator = backingStore.iterator();
        final var queueState = new ListWritableQueueState<>("SERVICE_NAME", "KEY", backingStore);
        assertThat(queueState.iterateOnDataSource().next()).isEqualTo(iterator.next());
    }
}
