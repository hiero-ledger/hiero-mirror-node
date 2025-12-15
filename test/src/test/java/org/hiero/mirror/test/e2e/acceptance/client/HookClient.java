// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.client;

import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.HookEntityId;
import com.hedera.hashgraph.sdk.HookId;
import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.LambdaMappingEntry;
import com.hedera.hashgraph.sdk.LambdaSStoreTransaction;
import com.hedera.hashgraph.sdk.LambdaStorageUpdate;
import com.hedera.hashgraph.sdk.Transaction;
import jakarta.inject.Named;
import java.util.List;
import org.hiero.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;
import org.hiero.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import org.hiero.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;
import org.springframework.retry.support.RetryTemplate;

/**
 * Client for creating and executing Hook-related transactions. This client handles hook attachment, LambdaStore
 * transactions, and other hook storage operations.
 */
@Named
public final class HookClient extends AbstractNetworkClient {

    public HookClient(
            SDKClient sdkClient, RetryTemplate retryTemplate, AcceptanceTestProperties acceptanceTestProperties) {
        super(sdkClient, retryTemplate, acceptanceTestProperties);
    }

    /**
     * Creates and executes a LambdaStore transaction for the specified account.
     *
     * @param account the account that owns the hook
     * @param hookId  the ID of the hook
     * @param key     the storage key as 256-bit byte array
     * @param value   the storage value as 256-bit byte array
     * @return NetworkTransactionResponse containing transaction ID and receipt
     */
    public NetworkTransactionResponse putStorageSlot(ExpandedAccountId account, long hookId, byte[] key, byte[] value) {
        // Create LambdaStore transaction using actual SDK implementation with proper storage updates
        var lambdaStoreTransaction = createLambdaStoreTransaction(account, hookId, key, value);

        return executeTransactionAndRetrieveReceipt(lambdaStoreTransaction);
    }

    /**
     * Creates a LambdaStore transaction with the specified parameters using legacy SDK classes following the same
     * structure as HapiLambdaSStore.
     */
    private Transaction<?> createLambdaStoreTransaction(
            ExpandedAccountId account, long hookId, byte[] key, byte[] value) {

        // Create storage operation using SDK method pattern
        // Since LambdaStorageUpdate is abstract, we need to use factory methods
        LambdaStorageUpdate storageUpdate = new LambdaStorageUpdate.LambdaStorageSlot(key, value);

        // Create the LambdaSStoreTransaction with hook ID
        var transaction = new LambdaSStoreTransaction()
                .setTransactionMemo("LambdaStore operation")
                .addStorageUpdate(storageUpdate)
                .setHookId(new HookId(new HookEntityId(account.getAccountId()), hookId))
                .setMaxTransactionFee(Hbar.fromTinybars(100_000_000L));

        // Finalize the transaction
        transaction = transaction.freezeWith(client).sign(account.getPrivateKey());

        return transaction;
    }

    /**
     * Creates a LambdaStore transaction for mapping entries. This would be used for Solidity mapping storage
     * operations.
     */
    public NetworkTransactionResponse putMappingEntry(
            ExpandedAccountId account, long hookId, byte[] mappingSlot, byte[] entryKey, byte[] entryValue) {
        LambdaMappingEntry mappingEntry = LambdaMappingEntry.ofKey(entryKey, entryValue);
        LambdaStorageUpdate mappingUpdate =
                new LambdaStorageUpdate.LambdaMappingEntries(mappingSlot, List.of(mappingEntry));

        // Create the LambdaSStoreTransaction for mapping operations
        var transaction = new LambdaSStoreTransaction()
                .setTransactionMemo("LambdaStore mapping operation")
                .addStorageUpdate(mappingUpdate)
                .setHookId(new HookId(new HookEntityId(account.getAccountId()), hookId))
                .setMaxTransactionFee(Hbar.fromTinybars(100_000_000L));

        return executeTransactionAndRetrieveReceipt(transaction, KeyList.of(account.getPrivateKey()));
    }

    @Override
    public int getOrder() {
        return 10; // Run cleanup after other clients
    }
}
