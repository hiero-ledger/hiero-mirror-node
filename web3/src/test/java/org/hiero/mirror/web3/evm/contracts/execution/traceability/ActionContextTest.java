// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import static org.assertj.core.api.Assertions.assertThat;

import org.hiero.mirror.rest.model.ActionResponse;
import org.hiero.mirror.rest.model.ActionResponse.TypeEnum;
import org.junit.jupiter.api.Test;

class ActionContextTest {

    @Test
    void dropsActionsBeyondMaxAndRecordsTruncationMarker() {
        final var context = new ActionContext();
        for (int i = 0; i < ActionContext.MAX_ACTIONS; i++) {
            context.addAction(new ActionResponse().type(TypeEnum.CALL).from("0x" + i), 0);
        }

        context.addAction(new ActionResponse().type(TypeEnum.CALL).from("0xoverflow"), 0);

        assertThat(context.isTruncated()).isTrue();
        assertThat(context.getActions()).hasSize(ActionContext.MAX_ACTIONS);
        assertThat(context.getActions().getLast().getError()).isEqualTo(ActionContext.TRUNCATED_ERROR);
        assertThat(context.getActions().getLast().getType()).isEqualTo(TypeEnum.CALL);
        assertThat(context.getActions().getLast().getFrom()).isEqualTo("0x" + (ActionContext.MAX_ACTIONS - 1));
    }

    @Test
    void dropsActionsAtDepth1024() {
        final var context = new ActionContext();
        context.addAction(new ActionResponse().type(TypeEnum.CALL).from("0x0"), 0);

        context.addAction(new ActionResponse().type(TypeEnum.CALL).from("0xdeep"), ActionContext.MAX_DEPTH);

        assertThat(context.isTruncated()).isTrue();
        assertThat(context.getActions()).hasSize(1);
        assertThat(context.getActions().getFirst().getError()).isEqualTo(ActionContext.TRUNCATED_ERROR);
        assertThat(context.getActions().getFirst().getFrom()).isEqualTo("0x0");
    }

    @Test
    void acceptsDepthJustBelowMax() {
        final var context = new ActionContext();
        var parent = new ActionResponse().type(TypeEnum.CALL).from("0x0");
        context.addAction(parent, 0);
        for (int depth = 1; depth < ActionContext.MAX_DEPTH; depth++) {
            final var child = new ActionResponse().type(TypeEnum.CALL).from("0x" + depth);
            context.addAction(child, depth);
            parent = child;
        }

        assertThat(context.isTruncated()).isFalse();
        assertThat(context.getActions()).hasSize(1);
        assertThat(context.getActionsByDepth(ActionContext.MAX_DEPTH - 1)).hasSize(1);
    }

    @Test
    void attachesNestedAndSiblingCallsWithoutDuplicatingInGetActions() {
        final var context = new ActionContext();
        final var root = new ActionResponse().from("0x0");
        final var child = new ActionResponse().from("0x1");
        final var sibling = new ActionResponse().from("0x2");

        context.addAction(root, 0);
        context.addAction(child, 1);
        context.finalizeAction(1, null, "0x1", "0x", null);
        context.addAction(sibling, 1);

        assertThat(root.getCalls()).containsExactly(child, sibling);
        assertThat(context.getActions()).containsExactly(root);
        assertThat(context.getActions()).doesNotContain(child, sibling);
        assertThat(context.getActions()).hasSize(1);
    }

    @Test
    void finalizeActionWritesResultAndClosesFrame() {
        final var context = new ActionContext();
        final var root = new ActionResponse().from("0x0");
        context.addAction(root, 0);

        assertThat(root.getCalls()).isEmpty();

        context.finalizeAction(0, null, "0xa", "0xbb", null);

        assertThat(root.getGasUsed()).isEqualTo("0xa");
        assertThat(root.getOutput()).isEqualTo("0xbb");
        assertThat(root.getError()).isNull();
    }

    @Test
    void recordsAllActionsByDepthInOrder() {
        final var context = new ActionContext();
        final var root = new ActionResponse().from("0x0");
        final var child = new ActionResponse().from("0x1");
        final var grandchild = new ActionResponse().from("0x2");
        final var sibling = new ActionResponse().from("0x3");

        context.addAction(root, 0);
        context.addAction(child, 1);
        context.addAction(grandchild, 2);
        context.finalizeAction(2, null, "0x1", "0x", null);
        context.finalizeAction(1, null, "0x2", "0x", null);
        context.addAction(sibling, 1);

        assertThat(context.getActions()).containsExactly(root);
        assertThat(context.getActionsByDepth(0)).containsExactly(root);
        assertThat(context.getActionsByDepth(1)).containsExactly(child, sibling);
        assertThat(context.getActionsByDepth(2)).containsExactly(grandchild);
        assertThat(context.getActionsByDepth(3)).isEmpty();
        assertThat(root.getCalls()).containsExactly(child, sibling);
        assertThat(child.getCalls()).containsExactly(grandchild);
    }

    @Test
    void dropsNestedActionWithoutParent() {
        final var context = new ActionContext();

        context.addAction(new ActionResponse().from("0xorphan"), 1);

        assertThat(context.isTruncated()).isTrue();
        assertThat(context.getActions()).isEmpty();
        assertThat(context.getActionsByDepth(1)).isEmpty();
    }

    @Test
    void finalizeActionPreservesTruncationError() {
        final var context = new ActionContext();
        final var root = new ActionResponse().from("0x0").type(TypeEnum.CALL);
        context.addAction(root, 0);
        for (int i = 1; i < ActionContext.MAX_ACTIONS; i++) {
            context.addAction(new ActionResponse().type(TypeEnum.CALL).from("0x" + i), 0);
        }
        context.addAction(new ActionResponse().type(TypeEnum.CALL).from("0xoverflow"), 0);

        context.finalizeAction(0, null, "0xa", "0xbb", null);

        assertThat(context.getActions().getLast().getError()).isEqualTo(ActionContext.TRUNCATED_ERROR);
        assertThat(context.getActions().getLast().getGasUsed()).isEqualTo("0xa");
        assertThat(context.getActions().getLast().getOutput()).isEqualTo("0xbb");
    }

    @Test
    void shouldCheckDeadlineEveryIntervalOpcodes() {
        final var context = new ActionContext();
        for (int i = 1; i < ActionContext.DEADLINE_CHECK_INTERVAL; i++) {
            assertThat(context.shouldCheckDeadline()).isFalse();
        }
        assertThat(context.shouldCheckDeadline()).isTrue();
        assertThat(context.shouldCheckDeadline()).isFalse();
    }

    @Test
    void finalizeActionIgnoresUnknownDepth() {
        final var context = new ActionContext();
        final var root = new ActionResponse().from("0x0");
        context.addAction(root, 0);

        context.finalizeAction(1, "err", "0x1", "0x", null);
        context.finalizeAction(-1, "err", "0x1", "0x", null);

        assertThat(root.getGasUsed()).isNull();
        assertThat(root.getError()).isNull();
    }
}
