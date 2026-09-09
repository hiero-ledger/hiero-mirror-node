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
        assertThat(context.getActions()).hasSize(ActionContext.MAX_ACTIONS + 1);
        assertThat(context.getActions().getLast().getError()).isEqualTo(ActionContext.TRUNCATED_ERROR);
        assertThat(context.getActions().getLast().getType()).isEqualTo(TypeEnum.UNKNOWN);
    }

    @Test
    void dropsActionsBeyondMaxDepth() {
        final var context = new ActionContext();
        context.addAction(new ActionResponse().type(TypeEnum.CALL).from("0x0"), 0);

        context.addAction(new ActionResponse().type(TypeEnum.CALL).from("0xdeep"), ActionContext.MAX_DEPTH + 1);

        assertThat(context.isTruncated()).isTrue();
        assertThat(context.getActions()).hasSize(2);
        assertThat(context.getActions().getLast().getError()).isEqualTo(ActionContext.TRUNCATED_ERROR);
    }

    @Test
    void attachesNestedAndSiblingCalls() {
        final var context = new ActionContext();
        final var root = new ActionResponse().from("0x0");
        final var child = new ActionResponse().from("0x1");
        final var sibling = new ActionResponse().from("0x2");

        context.addAction(root, 0);
        context.addAction(child, 1);
        context.finalizeAction(1, null, "0x1", "0x", null);
        context.addAction(sibling, 1);

        assertThat(context.getActions()).containsExactly(root, child, sibling);
        assertThat(root.getCalls()).containsExactly(child, sibling);
    }

    @Test
    void finalizeActionWritesResultAndClosesFrame() {
        final var context = new ActionContext();
        final var root = new ActionResponse().from("0x0");
        context.addAction(root, 0);

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

        assertThat(context.getActions()).containsExactly(root, child, sibling, grandchild);
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
        assertThat(context.getActions()).hasSize(1);
        assertThat(context.getActions().getLast().getError()).isEqualTo(ActionContext.TRUNCATED_ERROR);
        assertThat(context.getActionsByDepth(1)).isEmpty();
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
