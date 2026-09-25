// SPDX-License-Identifier: Apache-2.0

package com.swirlds.state.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.state.AbstractConcurrencyTest;
import org.hiero.mirror.web3.state.core.FunctionReadableSingletonState;
import org.junit.jupiter.api.Test;

final class ReadableSingletonStateBaseConcurrencyTest extends AbstractConcurrencyTest {

    private static final int STATE_ID = 104;
    private static final String SERVICE = "test-service";

    @Test
    void cachedReadStaysStableWhileConcurrentRequestSeesSupplierChange() throws Exception {
        final var backend = new AtomicReference<>("first");
        final var reads = new AtomicInteger();
        final var shared = new FunctionReadableSingletonState<>(SERVICE, STATE_ID, () -> {
            reads.incrementAndGet();
            return backend.get();
        });
        final var startB = new CountDownLatch(1);
        final var releaseA = new CountDownLatch(1);
        final var seenByA = new AtomicReference<String>();
        final var seenByB = new AtomicReference<String>();
        final var executor = Executors.newFixedThreadPool(2);
        try {
            final var futureA = executor.submit(() -> ContractCallContext.run(ctx -> {
                assertThat(shared.get()).isEqualTo("first");
                startB.countDown();
                await(releaseA);
                backend.set("changed-after-b");
                seenByA.set(shared.get());
                return null;
            }));
            final var futureB = executor.submit(() -> ContractCallContext.run(ctx -> {
                await(startB);
                backend.set("second");
                seenByB.set(shared.get());
                releaseA.countDown();
                return null;
            }));
            futureA.get(5, TimeUnit.SECONDS);
            futureB.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertThat(seenByB.get()).isEqualTo("second");
        assertThat(seenByA.get()).isEqualTo("first");
        assertThat(reads.get()).isEqualTo(2);
    }

    @Test
    void getDuringOneRequestDoesNotMarkConcurrentRequestRead() throws Exception {
        final var shared = new FunctionReadableSingletonState<>(SERVICE, STATE_ID, () -> "backend");
        final var startB = new CountDownLatch(1);
        final var releaseA = new CountDownLatch(1);
        final var readByB = new AtomicBoolean(true);
        final var executor = Executors.newFixedThreadPool(2);
        try {
            final var futureA = executor.submit(() -> ContractCallContext.run(ctx -> {
                shared.get();
                assertThat(shared.isRead()).isTrue();
                startB.countDown();
                await(releaseA);
                assertThat(shared.isRead()).isTrue();
                return null;
            }));
            final var futureB = executor.submit(() -> ContractCallContext.run(ctx -> {
                await(startB);
                readByB.set(shared.isRead());
                releaseA.countDown();
                return null;
            }));
            futureA.get(5, TimeUnit.SECONDS);
            futureB.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertThat(readByB.get()).isFalse();
    }

    @Test
    void resetDuringOneRequestDoesNotClearConcurrentRequest() throws Exception {
        final var backend = new AtomicReference<>("first");
        final var reads = new AtomicInteger();
        final var shared = new FunctionReadableSingletonState<>(SERVICE, STATE_ID, () -> {
            reads.incrementAndGet();
            return backend.get();
        });
        final var startB = new CountDownLatch(1);
        final var releaseA = new CountDownLatch(1);
        final var readByAAfterReset = new AtomicBoolean(false);
        final var seenByAAfterReset = new AtomicReference<String>();
        final var executor = Executors.newFixedThreadPool(2);
        try {
            final var futureA = executor.submit(() -> ContractCallContext.run(ctx -> {
                assertThat(shared.get()).isEqualTo("first");
                startB.countDown();
                await(releaseA);
                backend.set("should-not-be-used");
                readByAAfterReset.set(shared.isRead());
                seenByAAfterReset.set(shared.get());
                return null;
            }));
            final var futureB = executor.submit(() -> ContractCallContext.run(ctx -> {
                await(startB);
                shared.get();
                shared.reset();
                assertThat(shared.isRead()).isFalse();
                releaseA.countDown();
                return null;
            }));
            futureA.get(5, TimeUnit.SECONDS);
            futureB.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertThat(readByAAfterReset.get()).isTrue();
        assertThat(seenByAAfterReset.get()).isEqualTo("first");
        assertThat(reads.get()).isEqualTo(2);
    }
}
