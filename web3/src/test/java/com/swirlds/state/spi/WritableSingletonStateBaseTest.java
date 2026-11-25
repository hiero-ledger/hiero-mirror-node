// SPDX-License-Identifier: Apache-2.0

package com.swirlds.state.spi;

import static org.assertj.core.api.Assertions.assertThat;

import org.hiero.mirror.web3.ContextExtension;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.state.core.FunctionWritableSingletonState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ContextExtension.class)
final class WritableSingletonStateBaseTest {

    private static final int STATE_ID = 102;
    private static final String SERVICE = "test-service";

    @Test
    void putThenGetReturnsBufferedValue() {
        final var state = new FunctionWritableSingletonState<>(SERVICE, STATE_ID, () -> "backend");
        assertThat(state.isModified()).isFalse();

        state.put("from-put");

        assertThat(state.isModified()).isTrue();
        assertThat(state.get()).isEqualTo("from-put");
        assertThat(ContractCallContext.get().getWriteCacheState(STATE_ID))
                .isNotEmpty()
                .containsValue("from-put");
    }

    @Test
    void resetClearsWriteCacheAndHitsSupplier() {
        final var state = new FunctionWritableSingletonState<>(SERVICE, STATE_ID, () -> "backend");
        state.put("from-put");

        state.reset();

        assertThat(state.isModified()).isFalse();
        assertThat(ContractCallContext.get().getWriteCacheState(STATE_ID)).isEmpty();
        assertThat(state.get()).isEqualTo("backend");
    }

    @Test
    void committedValueTakesPrecedenceOverSupplier() {
        final var state = new FunctionWritableSingletonState<>(SERVICE, STATE_ID, () -> "backend");
        state.put("from-earlier-block");
        ContractCallContext.get().commitWriteCache();

        assertThat(ContractCallContext.get().getWriteCacheState(STATE_ID)).isEmpty();
        assertThat(state.get()).isEqualTo("from-earlier-block");
    }

    @Test
    void contextResetClearsWriteStaging() {
        final var state = new FunctionWritableSingletonState<>(SERVICE, STATE_ID, () -> "backend");
        state.put("from-put");

        ContractCallContext.get().reset();

        assertThat(state.isModified()).isFalse();
        assertThat(state.get()).isEqualTo("backend");
    }

    @Test
    void commitDoesNotClearWriteStaging() {
        final var state = new FunctionWritableSingletonState<>(SERVICE, STATE_ID, () -> "backend");
        state.put("from-put");

        state.commit();

        assertThat(state.isModified()).isTrue();
        assertThat(ContractCallContext.get().getWriteCacheState(STATE_ID)).containsValue("from-put");
        assertThat(state.get()).isEqualTo("from-put");

        state.put("from-nested-put");
        state.commit();
        assertThat(state.get()).isEqualTo("from-nested-put");
    }

    @Test
    void nullPutFallsBackToSupplier() {
        final var state = new FunctionWritableSingletonState<>(SERVICE, STATE_ID, () -> "backend");
        state.put(null);

        assertThat(state.isModified()).isTrue();
        assertThat(state.get()).isEqualTo("backend");
    }

    @Test
    void nestedWrapperCommitReachesDelegateOnlyWhenOuterCommits() {
        final var root = new FunctionWritableSingletonState<>(SERVICE, STATE_ID, () -> "backend");
        final var outer = new WrappedWritableSingletonState<>(root);
        final var inner = new WrappedWritableSingletonState<>(outer);

        inner.put("from-inner");
        assertThat(inner.isModified()).isTrue();
        assertThat(inner.get()).isEqualTo("from-inner");
        assertThat(outer.isModified()).isFalse();
        assertThat(outer.get()).isEqualTo("backend");
        assertThat(root.get()).isEqualTo("backend");

        inner.commit();
        assertThat(inner.isModified()).isFalse();
        assertThat(outer.get()).isEqualTo("from-inner");
        assertThat(root.isModified()).isFalse();
        assertThat(root.get()).isEqualTo("backend");

        outer.commit();
        assertThat(root.get()).isEqualTo("from-inner");
        assertThat(ContractCallContext.get().getWriteCacheState(STATE_ID)).containsValue("from-inner");
    }

    @Test
    void abandonedWrapperLeavesParentUnchanged() {
        final var root = new FunctionWritableSingletonState<>(SERVICE, STATE_ID, () -> "backend");
        final var outer = new WrappedWritableSingletonState<>(root);
        outer.put("from-outer");

        final var inner = new WrappedWritableSingletonState<>(outer);
        inner.put("from-inner");

        outer.commit();
        assertThat(root.get()).isEqualTo("from-outer");
    }

    @Test
    void nestedWrapperResetKeepsParentFrame() {
        final var root = new FunctionWritableSingletonState<>(SERVICE, STATE_ID, () -> "backend");
        final var outer = new WrappedWritableSingletonState<>(root);
        outer.put("from-outer");

        final var inner = new WrappedWritableSingletonState<>(outer);
        inner.put("from-inner");
        inner.reset();

        assertThat(inner.isModified()).isFalse();
        assertThat(inner.get()).isEqualTo("from-outer");
        assertThat(outer.get()).isEqualTo("from-outer");
    }

    @Test
    void wrapperReadsEarlierCommitUntilContextReset() {
        final var context = ContractCallContext.get();
        final var root = new FunctionWritableSingletonState<>(SERVICE, STATE_ID, () -> "backend");
        assertThat(root.get()).isEqualTo("backend");
        final var earlier = new WrappedWritableSingletonState<>(root);
        earlier.put("from-earlier-transaction");
        earlier.commit();

        assertThat(new WrappedWritableSingletonState<>(root).get()).isEqualTo("from-earlier-transaction");

        context.reset();
        assertThat(root.isModified()).isFalse();
        assertThat(new WrappedWritableSingletonState<>(root).get()).isEqualTo("backend");
    }
}
