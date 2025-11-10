// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hiero.mirror.common.domain.hook.AbstractHook;
import org.junit.jupiter.api.Test;

class HookExecutionCollectorTest {

    @Test
    void createEmptyCollector() {
        var collector = HookExecutionCollector.create();

        assertFalse(collector.hasHooks());
        assertEquals(0, collector.totalHookCount());
        assertTrue(collector.buildExecutionQueue().isEmpty());
    }

    @Test
    void addAllowExecHook() {
        var collector = HookExecutionCollector.create();

        collector.addAllowExecHook(101L, 1001L);

        assertTrue(collector.hasHooks());
        assertEquals(1, collector.totalHookCount());
        assertEquals(1, collector.allowExecHookIds().size());
        assertEquals(0, collector.allowPreExecHookIds().size());
        assertEquals(0, collector.allowPostExecHookIds().size());

        var queue = collector.buildExecutionQueue();
        assertEquals(1, queue.size());
        assertEquals(new AbstractHook.Id(101L, 1001L), queue.poll());
    }

    @Test
    void addPrePostExecHook() {
        var collector = HookExecutionCollector.create();

        collector.addPrePostExecHook(102L, 1002L);

        assertTrue(collector.hasHooks());
        assertEquals(2, collector.totalHookCount()); // Pre + Post = 2
        assertEquals(0, collector.allowExecHookIds().size());
        assertEquals(1, collector.allowPreExecHookIds().size());
        assertEquals(1, collector.allowPostExecHookIds().size());

        var queue = collector.buildExecutionQueue();
        assertEquals(2, queue.size());
        assertEquals(new AbstractHook.Id(102L, 1002L), queue.poll()); // Pre
        assertEquals(new AbstractHook.Id(102L, 1002L), queue.poll()); // Post
    }

    @Test
    void correctExecutionOrder() {
        var collector = HookExecutionCollector.create()
                .addAllowExecHook(101L, 1001L) // First: allowExec
                .addAllowExecHook(201L, 1001L)
                .addPrePostExecHook(102L, 1002L) // Second: allowPre, Third: allowPost
                .addPrePostExecHook(202L, 1002L);

        assertTrue(collector.hasHooks());
        assertEquals(6, collector.totalHookCount()); // 2 allowExec + 2 allowPre + 2 allowPost

        var queue = collector.buildExecutionQueue();
        assertEquals(6, queue.size());

        // Verify execution order: allowExec -> allowPre -> allowPost
        assertEquals(new AbstractHook.Id(101L, 1001L), queue.poll()); // allowExec[0]
        assertEquals(new AbstractHook.Id(201L, 1001L), queue.poll()); // allowExec[1]
        assertEquals(new AbstractHook.Id(102L, 1002L), queue.poll()); // allowPre[0]
        assertEquals(new AbstractHook.Id(202L, 1002L), queue.poll()); // allowPre[1]
        assertEquals(new AbstractHook.Id(102L, 1002L), queue.poll()); // allowPost[0] (same as allowPre[0])
        assertEquals(new AbstractHook.Id(202L, 1002L), queue.poll()); // allowPost[1] (same as allowPre[1])
    }

    @Test
    void methodChaining() {
        var queue = HookExecutionCollector.create()
                .addAllowExecHook(1L, 100L)
                .addPrePostExecHook(2L, 200L)
                .addAllowExecHook(3L, 300L)
                .buildExecutionQueue();

        assertEquals(4, queue.size());
        assertEquals(new AbstractHook.Id(1L, 100L), queue.poll());
        assertEquals(new AbstractHook.Id(3L, 300L), queue.poll());
        assertEquals(new AbstractHook.Id(2L, 200L), queue.poll());
        assertEquals(new AbstractHook.Id(2L, 200L), queue.poll());
    }
}
