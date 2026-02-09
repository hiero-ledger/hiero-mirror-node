// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.PrivateKey;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.rest.model.TransactionTypes;
import org.hiero.mirror.test.e2e.acceptance.client.AccountClient;
import org.hiero.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import org.hiero.mirror.test.e2e.acceptance.props.ExpandedAccountId;

@CustomLog
@RequiredArgsConstructor
public class BatchTransactionFeature {

    private final MirrorNodeClient mirrorClient;
    private final AccountClient accountClient;
    private final CommonProperties commonProperties;
    private final PrivateKey hollowAccountPrivateKey = PrivateKey.generateECDSA();

    private String batchTransactionId;
    private AccountId hollowResolvedAccountId;
    private String completionTransactionId;
    private ExpandedAccountId batchSigner;

    @When(
            "I submit a batch transaction containing transfer {long} tℏ to {string} and a hollow account create with {long} tℏ with batch signed by {string}")
    public void submitBatchWithHollowAndNormalTransfer(
            long normalTransferAmount,
            String recipientAccountName,
            long hollowFundingAmount,
            String batchSignerAccountName) {
        log.info("Submitting batch transaction (hollow auto-create + normal crypto transfer)");
        batchSigner = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(batchSignerAccountName));

        final var hollowAliasAccountId = AccountId.fromEvmAddress(
                hollowAccountPrivateKey.getPublicKey().toEvmAddress().toString(),
                commonProperties.getShard(),
                commonProperties.getRealm());
        final var batchResult = accountClient.submitBatchWithHollowAutoCreateAndNormalTransfer(
                batchSigner,
                hollowAliasAccountId,
                AccountClient.AccountNameEnum.valueOf(recipientAccountName),
                hollowFundingAmount,
                normalTransferAmount);

        assertNotNull(batchResult, "batchResult must be set");
        assertNotNull(batchResult.getTransactionIdStringNoCheckSum(), "batch transactionId must be set");

        hollowResolvedAccountId = AccountId.fromString(mirrorClient
                .getAccountDetailsUsingEvmAddress(hollowAliasAccountId)
                .getAccount());
        assertNotNull(hollowResolvedAccountId, "hollowResolvedAccountId must be set");

        batchTransactionId = batchResult.getTransactionIdStringNoCheckSum();
    }

    @When("I submit a transaction that completes the hollow account")
    public void completeHollow() {
        assertNotNull(hollowResolvedAccountId, "hollowResolvedAccountId must be set");
        log.info("Submitting completion transaction for hollow account {}", hollowResolvedAccountId);

        var completionResponse =
                accountClient.submitCompletionTransaction(hollowResolvedAccountId, hollowAccountPrivateKey);

        assertNotNull(completionResponse, "completionResponse must be set");
        assertNotNull(completionResponse.getTransactionId(), "completion transactionId must be set");

        completionTransactionId = completionResponse.getTransactionIdStringNoCheckSum();
    }

    @Then("I should see the batch transaction and completion transaction in the record stream")
    public void verifyBothInRecordStream() {
        assertNotNull(batchTransactionId, "batchTransactionId must be set");
        assertNotNull(completionTransactionId, "completionTransactionId must be set");

        final var transactions =
                mirrorClient.getTransactions(batchTransactionId).getTransactions();
        assertNotNull(transactions, "Batch transaction should appear in mirror node");
        assertThat(transactions).hasSize(4); // batch + 2 inner + hollow create

        final var atomicBatch = transactions.stream()
                .filter(t -> TransactionTypes.ATOMICBATCH.equals(t.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(atomicBatch, "Expected transaction list to have atomic batch transaction");

        // Matches inner transactions and hollow create. All should have parent timestamp of ATOMIC_BATCH consensus
        // timestamp
        final var innerTransactions = transactions.stream()
                .filter(t -> atomicBatch.getConsensusTimestamp().equals(t.getParentConsensusTimestamp()))
                .toList();
        assertThat(innerTransactions).hasSize(3);
        assertThat(innerTransactions)
                .filteredOn(t -> TransactionTypes.CRYPTOTRANSFER.equals(t.getName()))
                .hasSize(2)
                .allMatch(t -> batchSigner
                        .getPublicKey()
                        .toStringRaw()
                        .equals(t.getBatchKey().getKey()));
        assertThat(innerTransactions)
                .filteredOn(t -> TransactionTypes.CRYPTOCREATEACCOUNT.equals(t.getName()))
                .hasSize(1)
                .allMatch(t -> t.getBatchKey() == null);

        // ----- Completion transaction (separate transaction) -----
        var completionTransactions =
                mirrorClient.getTransactions(completionTransactionId).getTransactions();
        assertThat(completionTransactions).hasSize(2);

        assertThat(completionTransactions)
                .filteredOn(t -> TransactionTypes.CRYPTOTRANSFER.equals(t.getName()))
                .hasSize(1);

        assertThat(completionTransactions)
                .filteredOn(t -> TransactionTypes.CRYPTOUPDATEACCOUNT.equals(t.getName()))
                .hasSize(1);
    }
}
