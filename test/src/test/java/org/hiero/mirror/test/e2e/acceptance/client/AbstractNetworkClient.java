// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hashgraph.sdk.AccountBalanceQuery;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.Query;
import com.hedera.hashgraph.sdk.Status;
import com.hedera.hashgraph.sdk.Transaction;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.TransactionReceiptQuery;
import com.hedera.hashgraph.sdk.TransactionRecord;
import com.hedera.hashgraph.sdk.TransactionRecordQuery;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.Data;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;
import org.hiero.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import org.hiero.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.support.RetryTemplate;

@Data
abstract class AbstractNetworkClient implements Cleanable {

    private static final int MEMO_BYTES_MAX_LENGTH = 100;

    protected final Client client;
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final SDKClient sdkClient;
    protected final RetryTemplate retryTemplate;
    protected final AcceptanceTestProperties acceptanceTestProperties;

    public AbstractNetworkClient(
            SDKClient sdkClient, RetryTemplate retryTemplate, AcceptanceTestProperties acceptanceTestProperties) {
        this.sdkClient = sdkClient;
        this.client = sdkClient.getClient();
        this.retryTemplate = retryTemplate;
        this.acceptanceTestProperties = acceptanceTestProperties;
    }

    @Override
    public void clean() {
        // Nothing to clean up
    }

    protected final <T> void deleteAll(Collection<T> ids, Consumer<T> deleteAction) {
        try (var executorService = Executors.newCachedThreadPool()) {
            var futures = ids.stream()
                    .map(id -> (Callable<T>) () -> {
                        try {
                            deleteAction.accept(id);
                        } catch (Exception e) {
                            log.warn("Unable to delete {}: {}", id, e.getMessage());
                        }
                        return id;
                    })
                    .toList();

            executorService.invokeAll(futures);
            executorService.shutdown();
            executorService.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Unable to delete IDs: {}", ids, e);
        } finally {
            ids.clear();
        }
    }

    @SneakyThrows
    public <O, T extends Query<O, T>> O executeQuery(Supplier<Query<O, T>> querySupplier) {
        return retryTemplate.execute(x -> querySupplier.get().execute(client));
    }

    @SneakyThrows
    public TransactionId executeTransaction(Transaction<?> transaction, KeyList keyList, ExpandedAccountId payer) {
        if (payer != null) {
            transaction.setTransactionId(TransactionId.generate(payer.getAccountId()));
            transaction.freezeWith(client);
            transaction.sign(payer.getPrivateKey());
        }

        if (keyList != null) {
            transaction.freezeWith(client); // Signing requires transaction to be frozen
            for (var k : keyList) {
                if (k instanceof PrivateKey privateKey) {
                    transaction.sign(privateKey);
                }
            }
        }

        try {
            var transactionResponse = retryTemplate.execute(x -> transaction.execute(client));
            var transactionId = transactionResponse.transactionId;

            if (log.isDebugEnabled()) {
                var publicKeys = getSignatures(transaction);
                log.debug("Executed transaction {} with signatures: {}", transaction, publicKeys);
            }
            return transactionId;
        } catch (PrecheckStatusException e) {
            if (e.status == Status.INVALID_SIGNATURE) {
                var publicKeys = getSignatures(transaction);
                log.error("Invalid signature for transaction {} signed with: {}", transaction, publicKeys);
            }
            throw e;
        }
    }

    private Map<AccountId, Map<PublicKey, byte[]>> getSignatures(Transaction<?> transaction) {
        try {
            return transaction.getSignatures();
        } catch (Exception e) {
            return Map.of();
        }
    }

    public TransactionId executeTransaction(Transaction<?> transaction, KeyList keyList) {
        return executeTransaction(transaction, keyList, null);
    }

    public NetworkTransactionResponse executeTransactionAndRetrieveReceipt(
            Transaction<?> transaction, KeyList keyList, ExpandedAccountId payer) {
        var transactionId = executeTransaction(transaction, keyList, payer);
        var transactionReceipt = getTransactionReceipt(transactionId);
        log.debug("Executed {} {}", transaction.getClass().getSimpleName(), transactionId);

        return new NetworkTransactionResponse(transactionId, transactionReceipt);
    }

    public NetworkTransactionResponse executeTransactionAndRetrieveReceipt(
            Transaction<?> transaction, KeyList keyList) {
        return executeTransactionAndRetrieveReceipt(transaction, keyList, null);
    }

    public NetworkTransactionResponse executeTransactionAndRetrieveReceipt(
            Transaction<?> transaction, ExpandedAccountId payer) {
        return executeTransactionAndRetrieveReceipt(transaction, null, payer);
    }

    public NetworkTransactionResponse executeTransactionAndRetrieveReceipt(Transaction<?> transaction) {
        return executeTransactionAndRetrieveReceipt(transaction, null, null);
    }

    public TransactionReceipt getTransactionReceipt(TransactionId transactionId) {
        var query = new TransactionReceiptQuery().setTransactionId(transactionId);
        var receipt = executeQuery(() -> query);
        log.debug("Transaction receipt: {}", receipt);

        if (receipt.status != Status.SUCCESS) {
            throw new NetworkException("Transaction was unsuccessful: " + receipt);
        }

        return receipt;
    }

    @SneakyThrows
    public TransactionRecord getTransactionRecord(TransactionId transactionId) {
        return retryTemplate.execute(x -> {
            var receipt = new TransactionReceiptQuery()
                    .setTransactionId(transactionId)
                    .execute(client);
            if (receipt.status != Status.SUCCESS) {
                throw new RuntimeException(
                        String.format("Transaction %s is unsuccessful: %s", transactionId, receipt.status));
            }

            return new TransactionRecordQuery().setTransactionId(transactionId).execute(client);
        });
    }

    public long getBalance() {
        return getBalance(sdkClient.getExpandedOperatorAccountId());
    }

    public long getBalance(ExpandedAccountId accountId) {
        var query = new AccountBalanceQuery().setAccountId(accountId.getAccountId());
        var balance = executeQuery(() -> query).hbars;
        log.debug("Account {} balance is {}", accountId, balance);
        return balance.toTinybars();
    }

    protected String getMemo(String message) {
        String memo = String.format("Mirror Node acceptance test: %s %s", Instant.now(), message);
        return StringUtils.truncate(memo, MEMO_BYTES_MAX_LENGTH); // Memos are capped at 100 bytes
    }

    public void validateAddress(final String actualAddress) {
        final var account = getSdkClient().getExpandedOperatorAccountId();

        assertThat(actualAddress)
                .isEqualTo(
                        account.getPublicKey().isECDSA()
                                ? account.getPublicKey().toEvmAddress().toString()
                                : account.getAccountId().toEvmAddress());
    }

    // Returns "true" if the entities should be deleted
    protected final <T> boolean deleteOrLogEntities(Collection<T> ids, Consumer<T> deleteAction) {
        if (acceptanceTestProperties.isSkipEntitiesCleanup()) {
            logEntities();
            return false;
        }

        deleteAll(ids, deleteAction);
        return true;
    }

    protected void logEntities() {}
}
