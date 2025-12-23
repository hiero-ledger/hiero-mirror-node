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
import com.hedera.hashgraph.sdk.LambdaSStoreTransaction;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.TransferTransaction;
import jakarta.inject.Named;
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

    public LambdaSStoreTransaction getLambdaSStoreTransaction(ExpandedAccountId account, long hookId) {
        return new LambdaSStoreTransaction()
                .setTransactionMemo("LambdaStore mapping operation")
                .setHookId(new HookId(new HookEntityId(account.getAccountId()), hookId))
                .setMaxTransactionFee(Hbar.from(1));
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
