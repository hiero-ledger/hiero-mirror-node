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
}
