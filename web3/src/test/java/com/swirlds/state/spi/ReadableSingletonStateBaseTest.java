// SPDX-License-Identifier: Apache-2.0

package com.swirlds.state.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.hiero.mirror.web3.ContextExtension;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.state.core.FunctionReadableSingletonState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ContextExtension.class)
final class ReadableSingletonStateBaseTest {

    private static final int STATE_ID = 101;
    private static final String SERVICE = "test-service";

    @Test
    void secondGetInSameContextIsServedFromReadCache() {
        final var reads = new AtomicInteger();
        final var state = new FunctionReadableSingletonState<>(SERVICE, STATE_ID, () -> {
            reads.incrementAndGet();
            return "backend";
        });

        assertThat(state.isRead()).isFalse();
        assertThat(state.get()).isEqualTo("backend");
        assertThat(state.get()).isEqualTo("backend");
        assertThat(reads.get()).isEqualTo(1);
        assertThat(state.isRead()).isTrue();
        assertThat(ContractCallContext.get().getReadCacheState(STATE_ID)).isNotEmpty();
    }

    @Test
    void resetClearsReadCache() {
        final var reads = new AtomicInteger();
        final var state = new FunctionReadableSingletonState<>(SERVICE, STATE_ID, () -> {
            reads.incrementAndGet();
            return "backend";
        });
        state.get();

        state.reset();

        assertThat(state.isRead()).isFalse();
        assertThat(ContractCallContext.get().getReadCacheState(STATE_ID)).isEmpty();
        assertThat(state.get()).isEqualTo("backend");
        assertThat(reads.get()).isEqualTo(2);
    }

    @Test
    void secondContextDoesNotSeeFirstContextCache() {
        final var backend = new AtomicReference<>("first");
        final var state = new FunctionReadableSingletonState<>(SERVICE, STATE_ID, backend::get);

        assertThat(state.get()).isEqualTo("first");

        ContractCallContext.run(ctx -> {
            backend.set("second");
            assertThat(state.get()).isEqualTo("second");
            return null;
        });

        backend.set("should-not-be-used");
        assertThat(state.get()).isEqualTo("first");
    }

    @Test
    void getInOneContextDoesNotMarkAnotherContextRead() {
        final var state = new FunctionReadableSingletonState<>(SERVICE, STATE_ID, () -> "backend");
        state.get();
        assertThat(state.isRead()).isTrue();

        ContractCallContext.run(ctx -> {
            assertThat(state.isRead()).isFalse();
            assertThat(ContractCallContext.get().getReadCacheState(STATE_ID)).isEmpty();
            return null;
        });

        assertThat(state.isRead()).isTrue();
        assertThat(ContractCallContext.get().getReadCacheState(STATE_ID)).containsValue("backend");
    }

    @Test
    void resetInOneContextDoesNotClearAnotherContext() {
        final var backend = new AtomicReference<>("first");
        final var reads = new AtomicInteger();
        final var state = new FunctionReadableSingletonState<>(SERVICE, STATE_ID, () -> {
            reads.incrementAndGet();
            return backend.get();
        });
        state.get();

        ContractCallContext.run(ctx -> {
            assertThat(state.get()).isEqualTo("first");
            state.reset();
            assertThat(state.isRead()).isFalse();
            assertThat(ContractCallContext.get().getReadCacheState(STATE_ID)).isEmpty();
            return null;
        });

        backend.set("should-not-be-used");
        assertThat(state.isRead()).isTrue();
        assertThat(state.get()).isEqualTo("first");
        assertThat(reads.get()).isEqualTo(2);
    }
}
