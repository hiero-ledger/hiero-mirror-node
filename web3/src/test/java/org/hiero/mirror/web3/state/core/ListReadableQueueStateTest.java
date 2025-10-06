// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.core;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Iterator;
import java.util.Queue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings({"unchecked"})
@ExtendWith(MockitoExtension.class)
class ListReadableQueueStateTest {

    @Mock
    private Queue<Object> backingStore;

    @Test
    void testIterateOnDataSource() {
        final var iterator = mock(Iterator.class);
        when(backingStore.iterator()).thenReturn(iterator);
        final var queueState = new ListReadableQueueState<>("SERVICE_NAME", "KEY", backingStore);
        assertThat(queueState.iterateOnDataSource()).isEqualTo(iterator);
    }

    @Test
    void testPeekOnDataSource() {
        final var firstElem = new Object();
        when((backingStore.peek())).thenReturn(firstElem);
        final var queueState = new ListReadableQueueState<>("SERVICE_NAME", "KEY", backingStore);
        assertThat(queueState.peekOnDataSource()).isEqualTo(firstElem);
    }
}
