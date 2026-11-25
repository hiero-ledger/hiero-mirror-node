// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.rest.model.ActionResponse;
import org.hiero.mirror.rest.model.ActionResponse.TypeEnum;
import org.junit.jupiter.api.Test;

class ActionContextTest {

    @Test
    void dropsActionsBeyondMaxAndRecordsTruncationMarker() {
        final var context = new ActionContext();
        for (int i = 0; i < ActionContext.MAX_ACTIONS; i++) {
            context.addAction(new ActionResponse().type(TypeEnum.CALL).from("0x" + i), 0, 0L);
        }

        context.addAction(new ActionResponse().type(TypeEnum.CALL).from("0xoverflow"), 0, 0L);

        assertThat(context.isTruncated()).isTrue();
        assertThat(context.getActions()).hasSize(ActionContext.MAX_ACTIONS);
        assertThat(context.getActions().getLast().getError()).isEqualTo(ActionContext.TRUNCATED_ERROR);
        assertThat(context.getActions().getLast().getType()).isEqualTo(TypeEnum.CALL);
        assertThat(context.getActions().getLast().getFrom()).isEqualTo("0x" + (ActionContext.MAX_ACTIONS - 1));
    }

    @Test
    void dropsActionsBeyondRetainedDepth() {
        final var context = new ActionContext();
        context.addAction(new ActionResponse().type(TypeEnum.CALL).from("0x0"), 0, 0L);

        context.addAction(new ActionResponse().type(TypeEnum.CALL).from("0xdeep"), ActionContext.MAX_DEPTH, 0L);

        assertThat(context.isTruncated()).isTrue();
        assertThat(context.getActions()).hasSize(1);
        assertThat(context.getActions().getFirst().getError()).isEqualTo(ActionContext.TRUNCATED_ERROR);
        assertThat(context.getActions().getFirst().getFrom()).isEqualTo("0x0");
    }

    @Test
    void acceptsDepthJustBelowMax() {
        final var context = new ActionContext();
        var parent = new ActionResponse().type(TypeEnum.CALL).from("0x0");
        context.addAction(parent, 0, 0L);
        for (int depth = 1; depth < ActionContext.MAX_DEPTH; depth++) {
            final var child = new ActionResponse().type(TypeEnum.CALL).from("0x" + depth);
            context.addAction(child, depth, 0L);
            parent = child;
        }

        assertThat(context.isTruncated()).isFalse();
        assertThat(context.getActions()).hasSize(1);
        assertThat(context.getCurrentAction(ActionContext.MAX_DEPTH - 1)).isSameAs(parent);
    }

    @Test
    void serializesTruncatedTreeWithinProductionNestingLimit() throws Exception {
        final var context = new ActionContext();
        for (int depth = 0; depth <= ActionContext.MAX_DEPTH; depth++) {
            context.addAction(new ActionResponse().type(TypeEnum.CALL), depth, 0L);
        }
        final var factory = new com.fasterxml.jackson.databind.MappingJsonFactory();
        factory.setStreamWriteConstraints(com.fasterxml.jackson.core.StreamWriteConstraints.builder()
                .maxNestingDepth(100)
                .build());
        final var mapper = new com.fasterxml.jackson.databind.ObjectMapper(factory);

        assertThat(mapper.writeValueAsString(context.getActions())).contains(ActionContext.TRUNCATED_ERROR);
        assertThat(context.isTruncated()).isTrue();
    }

    @Test
    void attachesNestedAndSiblingCallsWithoutDuplicatingInGetActions() {
        final var context = new ActionContext();
        final var root = new ActionResponse().from("0x0");
        final var child = new ActionResponse().from("0x1");
        final var sibling = new ActionResponse().from("0x2");

        context.addAction(root, 0, 0L);
        context.addAction(child, 1, 0L);
        context.finalizeAction(1, null, "0x1", "0x", null);
        context.addAction(sibling, 1, 0L);

        assertThat(root.getCalls()).containsExactly(child, sibling);
        assertThat(context.getActions()).containsExactly(root);
        assertThat(context.getActions()).doesNotContain(child, sibling);
        assertThat(context.getActions()).hasSize(1);
    }

    @Test
    void finalizeActionWritesResultAndClosesFrame() {
        final var context = new ActionContext();
        final var root = new ActionResponse().from("0x0");
        context.addAction(root, 0, 0L);

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

        context.addAction(root, 0, 0L);
        context.addAction(child, 1, 0L);
        context.addAction(grandchild, 2, 0L);
        context.finalizeAction(2, null, "0x1", "0x", null);
        context.finalizeAction(1, null, "0x2", "0x", null);
        context.addAction(sibling, 1, 0L);

        assertThat(context.getActions()).containsExactly(root);
        assertThat(context.getCurrentAction(0)).isSameAs(root);
        assertThat(context.getCurrentAction(1)).isSameAs(sibling);
        assertThat(context.getCurrentAction(2)).isSameAs(grandchild);
        assertThat(context.getCurrentAction(3)).isNull();
        assertThat(root.getCalls()).containsExactly(child, sibling);
        assertThat(child.getCalls()).containsExactly(grandchild);
    }

    @Test
    void dropsNestedActionWithoutParent() {
        final var context = new ActionContext();

        context.addAction(new ActionResponse().from("0xorphan"), 1, 0L);

        assertThat(context.isTruncated()).isTrue();
        assertThat(context.getActions()).hasSize(1);
        assertThat(context.getActions().getFirst().getError()).isEqualTo(ActionContext.TRUNCATED_ERROR);
        assertThat(context.getActions().getFirst().getFrom()).isNull();
        assertThat(context.getCurrentAction(1)).isNull();
    }

    @Test
    void finalizeActionPreservesTruncationError() {
        final var context = new ActionContext();
        final var root = new ActionResponse().from("0x0").type(TypeEnum.CALL);
        context.addAction(root, 0, 0L);
        for (int i = 1; i < ActionContext.MAX_ACTIONS; i++) {
            context.addAction(new ActionResponse().type(TypeEnum.CALL).from("0x" + i), 0, 0L);
        }
        context.addAction(new ActionResponse().type(TypeEnum.CALL).from("0xoverflow"), 0, 0L);

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
    void beginCallResetsTruncationAndRecordsTheNextRoot() {
        final var context = new ActionContext();
        for (int i = 0; i < ActionContext.MAX_ACTIONS; i++) {
            context.addAction(new ActionResponse().type(TypeEnum.CALL).from("0x" + i), 0, 0L);
        }
        context.addAction(new ActionResponse().type(TypeEnum.CALL).from("0xoverflow"), 0, 0L);
        assertThat(context.isTruncated()).isTrue();
        final var truncated = context.getActions().getLast();
        assertThat(truncated.getError()).isEqualTo(ActionContext.TRUNCATED_ERROR);

        for (int i = 1; i < ActionContext.DEADLINE_CHECK_INTERVAL; i++) {
            assertThat(context.shouldCheckDeadline()).isFalse();
        }

        context.beginCall();

        assertThat(context.isTruncated()).isFalse();
        assertThat(context.shouldCheckDeadline()).isTrue();
        final var next = new ActionResponse().type(TypeEnum.CALL).from("0xnext");
        context.addAction(next, 0, 0L);
        assertThat(context.isTruncated()).isTrue();
        assertThat(context.getActions()).hasSize(ActionContext.MAX_ACTIONS + 1);
        assertThat(context.getActions()).doesNotContain(next);
        assertThat(truncated.getError()).isEqualTo(ActionContext.TRUNCATED_ERROR);
        assertThat(context.getActions().getLast()).isNotSameAs(truncated);
        assertThat(context.getActions().getLast().getError()).isEqualTo(ActionContext.TRUNCATED_ERROR);
    }

    @Test
    void byteBudgetTruncatesLargeOutput() {
        final var context = new ActionContext();
        final var root = new ActionResponse().type(TypeEnum.CALL).from("0x0").input("0x");
        context.addAction(root, 0, 0L);

        context.finalizeAction(0, null, "0x1", "ab".repeat(ActionContext.MAX_RETAINED_BYTES), null);

        assertThat(context.isTruncated()).isTrue();
        assertThat(root.getError()).isEqualTo(ActionContext.TRUNCATED_ERROR);
        assertThat(root.getGasUsed()).isEqualTo("0x1");
        assertThat(root.getOutput()).isNull();
    }

    @Test
    void retainedBytesSurviveBeginCall() {
        final var context = new ActionContext();
        final var first = new ActionResponse().type(TypeEnum.CALL).input("a".repeat(ActionContext.MAX_RETAINED_BYTES));
        context.addAction(first, 0, 0L);
        assertThat(context.isTruncated()).isFalse();

        context.beginCall();
        context.addAction(new ActionResponse().type(TypeEnum.CALL).input("b"), 0, 0L);

        assertThat(context.isTruncated()).isTrue();
        assertThat(first.getError()).isNull();
        assertThat(context.getActions()).hasSize(2);
        assertThat(context.getActions().getFirst()).isSameAs(first);
        final var marker = context.getActions().getLast();
        assertThat(marker.getError()).isEqualTo(ActionContext.TRUNCATED_ERROR);
        assertThat(marker.getInput()).isNull();
        assertThat(marker.getFrom()).isNull();
    }

    @Test
    void hexWithinBudgetOmitsPayloadThatDoesNotFit() {
        final var context = new ActionContext();
        context.addAction(new ActionResponse().input("a".repeat(ActionContext.MAX_RETAINED_BYTES - 4)), 0, 0L);
        final var previous = context.getActions().getFirst();

        context.beginCall();
        final var encoded = context.hexWithinBudget(Bytes.wrap(new byte[2]));

        assertThat(encoded).isNull();
        assertThat(context.isTruncated()).isTrue();
        assertThat(previous.getError()).isNull();
        assertThat(context.getActions().getLast().getError()).isEqualTo(ActionContext.TRUNCATED_ERROR);
        assertThat(new ActionContext().hexWithinBudget(Bytes.fromHexString("0xab")))
                .isEqualTo("0xab");
    }

    @Test
    void beginCallKeepsCompletedBodiesAndStartsANewTree() {
        final var context = new ActionContext();
        final var first = new ActionResponse().from("0x0");
        final var firstChild = new ActionResponse().from("0x1");
        context.addAction(first, 0, 0L);
        context.addAction(firstChild, 1, 0L);

        context.beginCall();

        assertThat(context.getActions()).containsExactly(first);
        assertThat(context.hasActionAt(0)).isFalse();
        assertThat(first.getCalls()).containsExactly(firstChild);

        final var second = new ActionResponse().from("0x2");
        context.addAction(second, 0, 0L);

        assertThat(context.getActions()).containsExactly(first, second);
        assertThat(context.getCurrentAction(0)).isEqualTo(second);
    }

    @Test
    void finalizeActionIgnoresUnknownDepth() {
        final var context = new ActionContext();
        final var root = new ActionResponse().from("0x0");
        context.addAction(root, 0, 0L);

        context.finalizeAction(1, "err", "0x1", "0x", null);
        context.finalizeAction(-1, "err", "0x1", "0x", null);

        assertThat(root.getGasUsed()).isNull();
        assertThat(root.getError()).isNull();
    }
}
