// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.client;

import com.google.common.base.Stopwatch;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.Query;
import com.hedera.hashgraph.sdk.SubscriptionHandle;
import com.hedera.hashgraph.sdk.TopicCreateTransaction;
import com.hedera.hashgraph.sdk.TopicId;
import com.hedera.hashgraph.sdk.TopicMessageQuery;
import com.hedera.hashgraph.sdk.TopicMessageSubmitTransaction;
import com.hedera.hashgraph.sdk.Transaction;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.TransactionReceiptQuery;
import com.hedera.hashgraph.sdk.TransactionResponse;
import jakarta.inject.Named;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.hiero.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.RetryOperations;
import org.springframework.retry.policy.TimeoutRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

/**
 * StartupProbe -- a helper class to validate a SDKClient before using it.
 */
@CustomLog
@Named
@RequiredArgsConstructor
public class StartupProbe {

    private static final Duration WAIT = Duration.ofSeconds(30L);

    private final AcceptanceTestProperties acceptanceTestProperties;
    private final RestClient.Builder restClient;

    public TransactionReceipt validateEnvironment(Client client) {
        var startupTimeout = acceptanceTestProperties.getStartupTimeout();
        var stopwatch = Stopwatch.createStarted();

        if (startupTimeout.equals(Duration.ZERO)) {
            log.warn("Startup probe disabled");
            return null;
        }

        // Adjust these lower to recover faster since all nodes might be down during a reset. Restore at the end.
        var maxNodeBackoff = client.getNodeMaxBackoff();
        client.setNodeMaxBackoff(WAIT);
        client.setMaxNodeReadmitTime(WAIT);

        log.info("Creating a topic to confirm node connectivity");
        var transactionId = executeTransaction(client, stopwatch, TopicCreateTransaction::new).transactionId;
        var receiptQuery = new TransactionReceiptQuery().setTransactionId(transactionId);
        var receipt = executeQuery(client, stopwatch, () -> receiptQuery);
        var topicId = receipt.topicId;
        log.info("Created topic {} successfully", topicId);

        callRestEndpoint(stopwatch, transactionId);
        long startTime;

        // Submit a topic message and ensure it's seen by mirror node to ensure the importer is caught up
        do {
            startTime = System.currentTimeMillis();
            submitMessage(client, stopwatch, topicId);
        } while (System.currentTimeMillis() - startTime > 15_000);

        client.setNodeMaxBackoff(maxNodeBackoff);
        client.setMaxNodeReadmitTime(maxNodeBackoff);

        log.info("Startup probe successful");
        return receipt;
    }

    @SneakyThrows
    private void submitMessage(Client client, Stopwatch stopwatch, TopicId topicId) {
        var messageLatch = new CountDownLatch(1);
        var startupTimeout = acceptanceTestProperties.getStartupTimeout();
        SubscriptionHandle subscription = null;

        try {
            log.info("Submitting a message to the network");
            var transactionIdMessage = executeTransaction(client, stopwatch, () -> new TopicMessageSubmitTransaction()
                            .setTopicId(topicId)
                            .setMessage("Mirror Node acceptance test"))
                    .transactionId;

            executeQuery(client, stopwatch, () -> new TransactionReceiptQuery().setTransactionId(transactionIdMessage));
            log.info("Waiting for the mirror node to publish the topic message");
            callRestEndpoint(stopwatch, transactionIdMessage);
            var retry = retryOperations(stopwatch);
            long remaining;

            do {
                if (subscription != null) {
                    subscription.unsubscribe();
                    subscription = null;
                }

                log.info("Subscribing to topic {}", topicId);
                subscription = retry.execute(x -> new TopicMessageQuery()
                        .setTopicId(topicId)
                        .setMaxAttempts(Integer.MAX_VALUE)
                        .setRetryHandler(t -> {
                            log.info("Retrying exception: {}", t.getMessage());
                            return true;
                        })
                        .setStartTime(Instant.EPOCH)
                        .subscribe(client, resp -> messageLatch.countDown()));

                remaining = startupTimeout.minus(stopwatch.elapsed()).toNanos();

                if (remaining <= 0L) {
                    throw new TimeoutException("Timer expired while waiting on message latch");
                }
            } while (!messageLatch.await(Math.min(10_000_000_000L, remaining), TimeUnit.NANOSECONDS));

            log.info("Received the topic message");
        } finally {
            if (subscription != null) {
                subscription.unsubscribe();
            }
        }
    }

    @SneakyThrows
    private TransactionResponse executeTransaction(
            Client client, Stopwatch stopwatch, Supplier<Transaction<?>> transaction) {
        var retry = retryOperations(stopwatch);
        return retry.execute(
                r -> transaction.get().setMaxAttempts(Integer.MAX_VALUE).execute(client, WAIT));
    }

    @SneakyThrows
    private <T> T executeQuery(Client client, Stopwatch stopwatch, Supplier<Query<T, ?>> transaction) {
        var retry = retryOperations(stopwatch);
        return retry.execute(
                r -> transaction.get().setMaxAttempts(Integer.MAX_VALUE).execute(client, WAIT));
    }

    private void callRestEndpoint(Stopwatch stopwatch, TransactionId transactionId) {
        var startupTimeout = acceptanceTestProperties.getStartupTimeout();
        var properties = acceptanceTestProperties.getRestProperties();
        long timeout = startupTimeout.minus(stopwatch.elapsed()).toMillis();
        var retryTemplate = RetryTemplate.builder()
                .customPolicy(new TimeoutRetryPolicy(timeout) {
                    @Override
                    public boolean canRetry(RetryContext context) {
                        return super.canRetry(context) && properties.shouldRetry(context.getLastThrowable());
                    }
                })
                .exponentialBackoff(properties.getMinBackoff(), 2.0, properties.getMaxBackoff())
                .build();

        var restTransactionId = transactionId.accountId + "-" + transactionId.validStart.getEpochSecond() + "-"
                + transactionId.validStart.getNano();

        retryTemplate.execute(x -> restClient
                .build()
                .get()
                .uri("/transactions/{id}", restTransactionId)
                .retrieve()
                .body(String.class));
    }

    private RetryOperations retryOperations(Stopwatch stopwatch) {
        return RetryTemplate.builder()
                .exponentialBackoff(1000, 2.0, 10000)
                .withTimeout(acceptanceTestProperties.getStartupTimeout().minus(stopwatch.elapsed()))
                .withListener(new RetryListener() {
                    @Override
                    public <T, E extends Throwable> void onError(RetryContext r, RetryCallback<T, E> c, Throwable t) {
                        log.warn(
                                "Retry attempt #{} with error: {} {}", r.getRetryCount(), t.getClass(), t.getMessage());
                    }
                })
                .build();
    }
}
