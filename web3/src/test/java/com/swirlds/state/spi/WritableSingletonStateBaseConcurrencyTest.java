// SPDX-License-Identifier: Apache-2.0

package com.swirlds.state.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.state.AbstractConcurrencyTest;
import org.hiero.mirror.web3.state.core.FunctionWritableSingletonState;
import org.junit.jupiter.api.Test;

class WritableSingletonStateBaseConcurrencyTest extends AbstractConcurrencyTest {

    private static final int STATE_ID = 103;
    private static final String SERVICE = "test-service";

    @Test
    void putDuringOneRequestIsNotVisibleToConcurrentRequest() throws Exception {
        final var shared = new FunctionWritableSingletonState<>(SERVICE, STATE_ID, () -> "backend");
        final var startB = new CountDownLatch(1);
        final var releaseA = new CountDownLatch(1);
        final var seenByB = new AtomicReference<String>();
        final var executor = Executors.newFixedThreadPool(2);
        try {
            final var futureA = executor.submit(() -> ContractCallContext.run(ctx -> {
                shared.put("from-A");
                startB.countDown();
                await(releaseA);
                shared.commit();
                return null;
            }));
            final var futureB = executor.submit(() -> ContractCallContext.run(ctx -> {
                await(startB);
                seenByB.set(shared.get());
                releaseA.countDown();
                return null;
            }));
            futureA.get(5, TimeUnit.SECONDS);
            futureB.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertThat(seenByB.get()).isEqualTo("backend");
    }
}
