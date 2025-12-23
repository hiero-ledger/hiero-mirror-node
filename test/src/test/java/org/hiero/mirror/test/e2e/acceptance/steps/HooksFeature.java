// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.steps;

import static org.apache.commons.lang3.ArrayUtils.EMPTY_BYTE_ARRAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.trim;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.HookCreationDetails;
import com.hedera.hashgraph.sdk.HookExtensionPoint;
import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.LambdaEvmHook;
import com.hedera.hashgraph.sdk.LambdaMappingEntry;
import com.hedera.hashgraph.sdk.LambdaStorageUpdate;
import com.hedera.hashgraph.sdk.Status;
import io.cucumber.java.Before;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.bouncycastle.util.encoders.Hex;
import org.hiero.mirror.rest.model.Hook;
import org.hiero.mirror.rest.model.HooksResponse;
import org.hiero.mirror.rest.model.HooksStorageResponse;
import org.hiero.mirror.test.e2e.acceptance.client.AccountClient;
import org.hiero.mirror.test.e2e.acceptance.client.HookClient;
import org.hiero.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import org.hiero.mirror.test.e2e.acceptance.config.FeatureProperties;
import org.hiero.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import org.hiero.mirror.test.e2e.acceptance.props.Order;
import org.hiero.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;
import org.identityconnectors.common.Version;

@RequiredArgsConstructor
public class HooksFeature extends AbstractFeature {

    private static final byte[] EXPLICIT_SLOT_KEY = {1};
    private static final byte[] EXPLICIT_SLOT_VALUE = {2};
    private static final long HOOK_ID = 16389;
    private static final byte[] MAPPING_SLOT = {3};
    private static final byte[] MAPPING_KEY = {4};
    private static final byte[] MAPPING_VALUE = {5};

    private final AccountClient accountClient;
    private final FeatureProperties featureProperties;
    private final HookClient hookClient;
    private final MirrorNodeClient mirrorClient;

    private ExpandedAccountId account;
    private String hookStorageCreatedTimestamp;
    private byte[] transferKey;

    @Before
    public void before() {
        final var blocksResponse = mirrorClient.getBlocks(Order.DESC, 1);
        final var blocks = blocksResponse.getBlocks();
        assumeTrue(!blocks.isEmpty());
        final var version = Version.parse(blocks.getFirst().getHapiVersion());
        assumeTrue(version.getMinor() >= featureProperties.getHapiMinorVersionWithHooks());
    }

    @When("I attach a hook using existing contract to account {string}")
    public void attachHookToAccount(String accountName) {
        account = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountName));
        // Get the ERC contract with hook functions
        final var hookContract = getContract(ContractResource.ERC);

        final var lambdaEvmHook = new LambdaEvmHook(hookContract.contractId());
        final var hookCreationDetails =
                new HookCreationDetails(HookExtensionPoint.ACCOUNT_ALLOWANCE_HOOK, HOOK_ID, lambdaEvmHook);
        networkTransactionResponse =
                accountClient.updateAccount(account, updateTx -> updateTx.addHookToCreate(hookCreationDetails));
        assertThat(networkTransactionResponse)
                .extracting(NetworkTransactionResponse::getTransactionId)
                .isNotNull();
    }

    @RetryAsserts
    @Then("The mirror node REST API should return the account hook")
    public void verifyMirrorNodeAPIForAccountHook() {
        final var accountId = account.getAccountId().toString();
        final var hooksResponse = mirrorClient.getAccountHooks(accountId);
        assertThat(hooksResponse)
                .satisfies(r -> assertThat(r.getLinks()).isNotNull())
                .extracting(HooksResponse::getHooks, InstanceOfAssertFactories.LIST)
                .first(InstanceOfAssertFactories.type(Hook.class))
                .returns(false, Hook::getDeleted)
                .returns(HOOK_ID, Hook::getHookId)
                .returns(accountId, Hook::getOwnerId);
    }

    @When("I trigger hook execution via crypto transfer of {long} tℏ")
    public void triggerHookExecutionViaCryptoTransfer(long tinybar) {
        final var recipient = accountClient.getAccount(AccountClient.AccountNameEnum.OPERATOR);
        final var amount = Hbar.fromTinybars(tinybar);
        networkTransactionResponse = hookClient.sendCryptoTransferWithHook(
                account, recipient.getAccountId(), amount, HOOK_ID, account.getPrivateKey());
        assertThat(networkTransactionResponse)
                .extracting(NetworkTransactionResponse::getTransactionId)
                .isNotNull();
    }

    @When("I create a HookStore transaction with both explicit and implicit storage slots")
    public void createHookStorageSlots() {
        final var hookStoreTransaction = hookClient.getLambdaSStoreTransaction(account, HOOK_ID);

        // Create and add storage updates
        final var mappingEntry = LambdaMappingEntry.ofKey(MAPPING_KEY, MAPPING_VALUE);
        final var mappingUpdate = new LambdaStorageUpdate.LambdaMappingEntries(MAPPING_SLOT, List.of(mappingEntry));
        final var slotStorageUpdate = new LambdaStorageUpdate.LambdaStorageSlot(EXPLICIT_SLOT_KEY, EXPLICIT_SLOT_VALUE);
        hookStoreTransaction.addStorageUpdate(mappingUpdate).addStorageUpdate(slotStorageUpdate);

        networkTransactionResponse = hookClient.executeTransactionAndRetrieveReceipt(
                hookStoreTransaction, KeyList.of(account.getPrivateKey()));
        assertThat(networkTransactionResponse)
                .extracting(NetworkTransactionResponse::getTransactionId)
                .isNotNull();

        final var transactionsResponse =
                mirrorClient.getTransactions(networkTransactionResponse.getTransactionIdStringNoCheckSum());
        assertThat(transactionsResponse.getTransactions()).hasSize(1);
        hookStorageCreatedTimestamp =
                transactionsResponse.getTransactions().getFirst().getConsensusTimestamp();
    }

    @RetryAsserts
    @Then("The mirror node REST API should return hook storage entries")
    public void verifyMirrorNodeAPIForAccountHookStorage() {
        final var accountId = account.getAccountId().toString();
        final var hookStorageResponse = mirrorClient.getHookStorage(accountId, HOOK_ID);

        assertThat(hookStorageResponse)
                .returns(accountId, HooksStorageResponse::getOwnerId)
                .returns(HOOK_ID, HooksStorageResponse::getHookId)
                .satisfies(resp -> assertThat(resp.getLinks()).isNotNull())
                .extracting(HooksStorageResponse::getStorage, InstanceOfAssertFactories.LIST)
                .hasSize(3);

        // Capture the transfer key created by crypto transfer, its timestamp is different from the HookStorage
        // transaction's timestamp
        if (transferKey == null) {
            for (var storage : hookStorageResponse.getStorage()) {
                if (!storage.getTimestamp().equals(hookStorageCreatedTimestamp)) {
                    transferKey = trim(Hex.decode(storage.getKey().replaceFirst("0x", "")));
                    break;
                }
            }
        }
    }

    @When("I create a HookStore transaction to remove all storage slots")
    public void removeHookStorageSlots() {
        final var hookStoreTransaction = hookClient.getLambdaSStoreTransaction(account, HOOK_ID);

        // Create and add storage updates
        final var slotStorageUpdate = new LambdaStorageUpdate.LambdaStorageSlot(EXPLICIT_SLOT_KEY, EMPTY_BYTE_ARRAY);
        final var mappingEntry = LambdaMappingEntry.ofKey(MAPPING_KEY, EMPTY_BYTE_ARRAY);
        final var mappingUpdate = new LambdaStorageUpdate.LambdaMappingEntries(MAPPING_SLOT, List.of(mappingEntry));
        hookStoreTransaction.addStorageUpdate(mappingUpdate).addStorageUpdate(slotStorageUpdate);

        // Remove the key created during crypto transfer
        if (transferKey != null) {
            final var transferKeyUpdate = new LambdaStorageUpdate.LambdaStorageSlot(transferKey, EMPTY_BYTE_ARRAY);
            hookStoreTransaction.addStorageUpdate(transferKeyUpdate);
        }

        networkTransactionResponse = hookClient.executeTransactionAndRetrieveReceipt(
                hookStoreTransaction, KeyList.of(account.getPrivateKey()));
        assertThat(networkTransactionResponse)
                .extracting(NetworkTransactionResponse::getTransactionId)
                .isNotNull();

        // Clear storage tracking
        transferKey = null;
    }

    @RetryAsserts
    @Then("There should be no storage entry for hook")
    public void verifyEmptyStorageForAccount() {
        final var storageResponse =
                mirrorClient.getHookStorage(account.getAccountId().toString(), HOOK_ID);
        assertThat(storageResponse)
                .returns(HOOK_ID, HooksStorageResponse::getHookId)
                .extracting(HooksStorageResponse::getStorage, InstanceOfAssertFactories.LIST)
                .isEmpty();
    }

    @When("I delete hook")
    public void deleteHookFromAccount() {
        assertThat(account).extracting(ExpandedAccountId::getAccountId).isNotNull();
        networkTransactionResponse = accountClient.updateAccount(account, updateTx -> {
            updateTx.setAccountMemo("Hook " + HOOK_ID + " removal requested");
            updateTx.addHookToDelete(HOOK_ID);
        });

        assertThat(networkTransactionResponse)
                .extracting(NetworkTransactionResponse::getReceipt)
                .returns(Status.SUCCESS, r -> r.status);
    }

    @RetryAsserts
    @Then("The account should have no hooks attached")
    public void verifyNoHooksAttached() {
        final var accountId = account.getAccountId().toString();
        final var hooksResponse = mirrorClient.getAccountHooks(accountId);
        assertThat(hooksResponse)
                .satisfies(r -> assertThat(r.getLinks()).isNotNull())
                .extracting(HooksResponse::getHooks, InstanceOfAssertFactories.LIST)
                .hasSize(1)
                .first(InstanceOfAssertFactories.type(Hook.class))
                .returns(true, Hook::getDeleted)
                .returns(HOOK_ID, Hook::getHookId)
                .returns(accountId, Hook::getOwnerId);
    }
}
