// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.client;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.EvmHookCall;
import com.hedera.hashgraph.sdk.FungibleHookCall;
import com.hedera.hashgraph.sdk.FungibleHookType;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.HookEntityId;
import com.hedera.hashgraph.sdk.HookId;
import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.LambdaMappingEntry;
import com.hedera.hashgraph.sdk.LambdaSStoreTransaction;
import com.hedera.hashgraph.sdk.LambdaStorageUpdate;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.TransferTransaction;
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
    public NetworkTransactionResponse hookStoreSlot(ExpandedAccountId account, long hookId, byte[] key, byte[] value) {
        // Create LambdaStore transaction using actual SDK implementation with proper storage updates
        return executeHookStoreTransaction(account, hookId, key, value);
    }

    private NetworkTransactionResponse executeHookStoreTransaction(
            ExpandedAccountId account, long hookId, byte[] key, byte[] value) {

        // Create storage operation using SDK method pattern
        // Since LambdaStorageUpdate is abstract, we need to use factory methods
        LambdaStorageUpdate storageUpdate = new LambdaStorageUpdate.LambdaStorageSlot(key, value);

        return executeHookStoreTransaction(account, hookId, storageUpdate);
    }

    /**
     * Creates a LambdaStore transaction for mapping entries. This would be used for Solidity mapping storage
     * operations.
     */
    public NetworkTransactionResponse hookStoreMapping(
            ExpandedAccountId account, long hookId, byte[] mappingSlot, byte[] entryKey, byte[] entryValue) {
        final var mappingEntry = LambdaMappingEntry.ofKey(entryKey, entryValue);
        final var mappingUpdate = new LambdaStorageUpdate.LambdaMappingEntries(mappingSlot, List.of(mappingEntry));

        return executeHookStoreTransaction(account, hookId, mappingUpdate);
    }

    private NetworkTransactionResponse executeHookStoreTransaction(
            ExpandedAccountId account, long hookId, LambdaStorageUpdate mappingUpdate) {
        // Create the LambdaSStoreTransaction for mapping operations
        final var transaction = new LambdaSStoreTransaction()
                .setTransactionMemo("LambdaStore mapping operation")
                .addStorageUpdate(mappingUpdate)
                .setHookId(new HookId(new HookEntityId(account.getAccountId()), hookId))
                .setMaxTransactionFee(Hbar.fromTinybars(100_000_000L));

        return executeTransactionAndRetrieveReceipt(transaction, KeyList.of(account.getPrivateKey()));
    }

    /**
     * Send crypto transfer with hook execution - matches the pattern from TransferTransactionHooksIntegrationTest
     */
    public NetworkTransactionResponse sendCryptoTransferWithHook(
            ExpandedAccountId sender, AccountId recipient, Hbar hbarAmount, long hookId, PrivateKey privateKey) {
        // Create hook call with empty context data and higher gas limit for storage operations
        final var hookCall = new FungibleHookCall(
                hookId, new EvmHookCall(new byte[] {}, 100_000L), FungibleHookType.PRE_TX_ALLOWANCE_HOOK);

        final var transferTransaction = new TransferTransaction()
                .addHbarTransferWithHook(sender.getAccountId(), hbarAmount.negated(), hookCall)
                .addHbarTransfer(recipient, hbarAmount)
                .setTransactionMemo(getMemo("Crypto transfer with hook"));

        return executeTransactionAndRetrieveReceipt(
                transferTransaction, privateKey == null ? null : KeyList.of(privateKey), sender);
    }
}
