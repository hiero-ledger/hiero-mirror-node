// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.steps;

import static org.assertj.core.api.Assertions.assertThat;

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
    private AccountId hollowAliasAccountId;

    @When(
            "I submit a batch transaction containing transfer {long} tℏ to {string} and a hollow account create with {long} tℏ with batch signed by {string}")
    public void submitBatchWithHollowAndNormalTransfer(
            long normalTransferAmount,
            String recipientAccountName,
            long hollowFundingAmount,
            String batchSignerAccountName) {
        log.debug("Submitting batch transaction (hollow auto-create + normal crypto transfer)");
        batchSigner = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(batchSignerAccountName));

        hollowAliasAccountId = AccountId.fromEvmAddress(
                hollowAccountPrivateKey.getPublicKey().toEvmAddress().toString(),
                commonProperties.getShard(),
                commonProperties.getRealm());

        final var batchResult = accountClient.submitBatchWithHollowAutoCreateAndNormalTransfer(
                batchSigner,
                hollowAliasAccountId,
                AccountClient.AccountNameEnum.valueOf(recipientAccountName),
                hollowFundingAmount,
                normalTransferAmount);

        assertThat(batchResult).isNotNull();
        assertThat(batchResult.getTransactionIdStringNoCheckSum()).isNotNull();

        batchTransactionId = batchResult.getTransactionIdStringNoCheckSum();
    }

    @When("I submit a transaction that completes the hollow account")
    public void completeHollow() {
        assertThat(hollowResolvedAccountId).isNotNull();
        log.debug("Submitting completion transaction for hollow account {}", hollowResolvedAccountId);

        var completionResponse =
                accountClient.submitCompletionTransaction(hollowResolvedAccountId, hollowAccountPrivateKey);

        assertThat(completionResponse).isNotNull();
        assertThat(completionResponse.getTransactionId()).isNotNull();

        completionTransactionId = completionResponse.getTransactionIdStringNoCheckSum();
    }

    @Then("I should be able to resolve the hollow account id")
    public void resolveHollowAccountId() {
        final var accountDetails = mirrorClient.getAccountDetailsUsingEvmAddress(hollowAliasAccountId);

        hollowResolvedAccountId = AccountId.fromString(accountDetails.getAccount());

        assertThat(hollowResolvedAccountId).isNotNull();
        assertThat(accountDetails.getKey()).isNull();
    }

    @Then("I should see the batch transaction in mirror node")
    public void verifyBatchTransactionInRecordStream() {
        assertThat(batchTransactionId).isNotNull();

        final var transactions =
                mirrorClient.getTransactions(batchTransactionId).getTransactions();
        assertThat(transactions).isNotNull();
        assertThat(transactions).hasSize(4); // batch + 2 inner + hollow create

        final var atomicBatch = transactions.stream()
                .filter(t -> TransactionTypes.ATOMICBATCH.equals(t.getName()))
                .findFirst()
                .orElse(null);
        assertThat(atomicBatch);

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
    }

    @Then("I should see the completion transaction in mirror node")
    public void verifyCompletionTransaction() {
        assertThat(completionTransactionId).isNotNull();

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
