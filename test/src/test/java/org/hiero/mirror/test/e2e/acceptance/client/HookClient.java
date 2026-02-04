// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.client;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.EvmHookCall;
import com.hedera.hashgraph.sdk.EvmHookStorageUpdate;
import com.hedera.hashgraph.sdk.FungibleHookCall;
import com.hedera.hashgraph.sdk.FungibleHookType;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.HookEntityId;
import com.hedera.hashgraph.sdk.HookId;
import com.hedera.hashgraph.sdk.HookStoreTransaction;
import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.TransferTransaction;
import jakarta.inject.Named;
import java.util.List;
import org.hiero.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;
import org.hiero.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import org.hiero.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;
import org.springframework.core.retry.RetryTemplate;

/**
 * Client for creating and executing Hook-related transactions. This client handles hook attachment, HookStore
 * transactions, and other hook storage operations.
 */
@Named
public final class HookClient extends AbstractNetworkClient {

    public HookClient(
            SDKClient sdkClient, RetryTemplate retryTemplate, AcceptanceTestProperties acceptanceTestProperties) {
        super(sdkClient, retryTemplate, acceptanceTestProperties);
    }

    public NetworkTransactionResponse hookStore(
            ExpandedAccountId account, long hookId, List<EvmHookStorageUpdate> storageUpdates) {
        final var transaction = new HookStoreTransaction()
                .setTransactionMemo(getMemo("HookStore operation"))
                .setHookId(new HookId(new HookEntityId(account.getAccountId()), hookId))
                .setMaxTransactionFee(Hbar.from(1));
        storageUpdates.forEach(transaction::addStorageUpdate);
        return executeTransactionAndRetrieveReceipt(transaction, KeyList.of(account.getPrivateKey()));
    }

    /**
     * Send crypto transfer with hook execution - matches the pattern from TransferTransactionHooksIntegrationTest
     */
    public NetworkTransactionResponse sendCryptoTransferWithHook(
            ExpandedAccountId sender, AccountId recipient, Hbar amount, long hookId) {
        // Create hook call with empty context data and higher gas limit for storage operations
        final var hookCall = new FungibleHookCall(
                hookId, new EvmHookCall(new byte[] {}, 100_000L), FungibleHookType.PRE_TX_ALLOWANCE_HOOK);

        final var transferTransaction = new TransferTransaction()
                .addHbarTransferWithHook(sender.getAccountId(), amount.negated(), hookCall)
                .addHbarTransfer(recipient, amount)
                .setTransactionMemo(getMemo("Crypto transfer with hook"));

        return executeTransactionAndRetrieveReceipt(transferTransaction, sender);
    }
}
