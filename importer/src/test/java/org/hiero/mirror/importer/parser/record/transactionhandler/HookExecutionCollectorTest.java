// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;

import com.hederahashgraph.api.proto.java.HookCall;
import org.hiero.mirror.common.domain.hook.AbstractHook;
import org.junit.jupiter.api.Test;

final class HookExecutionCollectorTest {

    @Test
    void createEmptyCollector() {
        final var collector = HookExecutionCollector.create();

        assertThat(collector.allowExecHookIds().isEmpty()).isTrue();
        assertThat(collector.allowPreExecHookIds().isEmpty()).isTrue();
        assertThat(collector.allowPostExecHookIds().isEmpty()).isTrue();
        assertThat(collector.buildExecutionQueue().isEmpty()).isTrue();
    }

    @Test
    void addAllowExecHook() {
        // given
        final var collector = HookExecutionCollector.create();
        collector.addAllowExecHook(HookCall.newBuilder().setHookId(101L).build(), 1001L);
        final var expectedHookId = new AbstractHook.Id(101L, 1001L);

        // when, then
        assertThat(collector.allowExecHookIds()).containsExactly(expectedHookId);
        assertThat(collector.allowPreExecHookIds()).isEmpty();
        assertThat(collector.allowPostExecHookIds()).isEmpty();
        assertThat(collector.buildExecutionQueue()).containsExactly(expectedHookId);
    }

    @Test
    void addPrePostExecHook() {
        // given
        final var collector = HookExecutionCollector.create();
        collector.addPrePostExecHook(HookCall.newBuilder().setHookId(102L).build(), 1002L);
        final var expected = new AbstractHook.Id(102L, 1002L);

        // when, then
        assertThat(collector.allowExecHookIds()).isEmpty();
        assertThat(collector.allowPreExecHookIds()).containsExactly(expected);
        assertThat(collector.allowPostExecHookIds()).containsExactly(expected);
        assertThat(collector.buildExecutionQueue()).containsExactly(expected, expected);
    }

    @Test
    void correctExecutionOrder() {
        // given
        final var collector = HookExecutionCollector.create();
        collector.addAllowExecHook(HookCall.newBuilder().setHookId(101L).build(), 1001L);
        collector.addAllowExecHook(HookCall.newBuilder().setHookId(201L).build(), 1001L);
        collector.addPrePostExecHook(HookCall.newBuilder().setHookId(102L).build(), 1002L);
        collector.addPrePostExecHook(HookCall.newBuilder().setHookId(202L).build(), 1002L);

        // when, then
        final int totalHooks = collector.allowExecHookIds().size()
                + collector.allowPreExecHookIds().size()
                + collector.allowPostExecHookIds().size();
        assertThat(totalHooks).isEqualTo(6);
        assertThat(collector.allowExecHookIds().size()).isEqualTo(2);
        assertThat(collector.allowPreExecHookIds().size()).isEqualTo(2);
        assertThat(collector.allowPostExecHookIds().size()).isEqualTo(2);
        assertThat(collector.buildExecutionQueue())
                .containsExactly(
                        new AbstractHook.Id(101L, 1001L),
                        new AbstractHook.Id(201L, 1001L),
                        new AbstractHook.Id(102L, 1002L),
                        new AbstractHook.Id(202L, 1002L),
                        new AbstractHook.Id(102L, 1002L),
                        new AbstractHook.Id(202L, 1002L));
    }
}
