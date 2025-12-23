// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.leftPadBytes;

import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.HookCreationDetails;
import com.hedera.hashgraph.sdk.HookExtensionPoint;
import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.LambdaEvmHook;
import com.hedera.hashgraph.sdk.LambdaMappingEntry;
import com.hedera.hashgraph.sdk.LambdaStorageUpdate;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.List;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.binary.Hex;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.hiero.mirror.rest.model.Hook;
import org.hiero.mirror.rest.model.HooksStorageResponse;
import org.hiero.mirror.test.e2e.acceptance.client.AccountClient;
import org.hiero.mirror.test.e2e.acceptance.client.HookClient;
import org.hiero.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import org.hiero.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import org.hiero.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;
import org.hiero.mirror.test.e2e.acceptance.util.TestUtil;
import org.springframework.http.HttpStatus;

@CustomLog
@RequiredArgsConstructor
public class HooksFeature extends AbstractFeature {

    private static final byte[] EXPLICIT_SLOT_KEY = new byte[] {1};
    private static final byte[] EXPLICIT_SLOT_VALUE = new byte[] {2};
    private static final long HOOK_ID = 16389;
    private static final byte[] MAPPING_SLOT = new byte[] {3};
    private static final byte[] MAPPING_KEY = new byte[] {4};
    private static final byte[] MAPPING_VALUE = new byte[] {5};

    private final AccountClient accountClient;
    private final HookClient hookClient;
    private final MirrorNodeClient mirrorClient;

    private ExpandedAccountId account;
    private String transferKey;

    @When("I attach a hook using existing contract to account {string}")
    public void attachHookToAccount(String accountName) {
        this.account = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountName));
        // Get the ERC contract with hook functions
        final var hookContract = getContract(ContractResource.ERC);

        // Create LambdaEvmHook with the contract
        final var lambdaEvmHook = new LambdaEvmHook(hookContract.contractId());

        // Create HookCreationDetails
        final var hookCreationDetails =
                new HookCreationDetails(HookExtensionPoint.ACCOUNT_ALLOWANCE_HOOK, HOOK_ID, lambdaEvmHook);

        // Attach hook to account using AccountUpdateTransaction
        networkTransactionResponse = accountClient.updateAccount(account, updateTx -> {
            updateTx.addHookToCreate(hookCreationDetails);
        });
        assertThat(networkTransactionResponse)
                .extracting(NetworkTransactionResponse::getTransactionId)
                .isNotNull();
    }

    @Then("the mirror node REST API should return the account hook")
    @RetryAsserts
    public void verifyMirrorNodeAPIForAccountHook() {
        final var accountId = account.getAccountId().toString();

        // Call the hooks API and verify the hook exists
        final var hooksResponse = mirrorClient.getAccountHooks(accountId);

        assertThat(hooksResponse)
                .satisfies(r -> assertThat(r.getLinks()).isNotNull())
                .extracting(org.hiero.mirror.rest.model.HooksResponse::getHooks, InstanceOfAssertFactories.LIST)
                .first(InstanceOfAssertFactories.type(org.hiero.mirror.rest.model.Hook.class))
                .returns(false, Hook::getDeleted)
                .returns(HOOK_ID, Hook::getHookId)
                .returns(accountId, Hook::getOwnerId);
    }

    @When("I trigger hook execution via crypto transfer of {long} tℏ")
    public void triggerHookExecutionViaCryptoTransfer(long transferAmountInTinybar) {
        // Use the operator account as sender and specified account as recipient (like
        // TransferTransactionHooksIntegrationTest)
        final var recipient = accountClient.getAccount(AccountClient.AccountNameEnum.OPERATOR);

        // Convert tinybar to Hbar
        var hbarAmount = Hbar.fromTinybars(transferAmountInTinybar);

        // Execute crypto transfer with hook using the pattern from TransferTransactionHooksIntegrationTest
        networkTransactionResponse = hookClient.sendCryptoTransferWithHook(
                account, recipient.getAccountId(), hbarAmount, HOOK_ID, account.getPrivateKey());

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

        // Execute transaction
        final var networkTransactionResponse = hookClient.executeTransactionAndRetrieveReceipt(
                hookStoreTransaction, KeyList.of(account.getPrivateKey()));

        assertThat(networkTransactionResponse)
                .extracting(NetworkTransactionResponse::getTransactionId)
                .isNotNull();
    }

    @Then("the mirror node REST API should return hook storage entries")
    @RetryAsserts
    public void verifyMirrorNodeAPIForAccountHookStorage() {
        final var accountId = account.getAccountId().toString();
        final var hookStorageResponse = mirrorClient.getHookStorage(accountId, HOOK_ID);

        assertThat(hookStorageResponse)
                .returns(HOOK_ID, HooksStorageResponse::getHookId)
                .satisfies(resp -> assertThat(resp.getLinks()).isNotNull(), resp -> assertThat(resp.getStorage())
                        .isNotEmpty());

        // Capture the transfer key created by crypto transfer (should be the first entry not in our storageKeys)
        if (transferKey == null) {
            transferKey = hookStorageResponse.getStorage().getFirst().getKey();
        }
    }

    @When("I create a HookStore transaction to remove all storage slots")
    public void removeHookStorageSlots() {
        // Get the account that will perform the LambdaStore operation
        final var emptyValue = new byte[0];
        final var hookStoreTransaction = hookClient.getLambdaSStoreTransaction(account, HOOK_ID);

        // Create and add storage updates
        final var slotStorageUpdate = new LambdaStorageUpdate.LambdaStorageSlot(EXPLICIT_SLOT_KEY, emptyValue);
        final var mappingEntry = LambdaMappingEntry.ofKey(MAPPING_KEY, emptyValue);
        final var mappingUpdate = new LambdaStorageUpdate.LambdaMappingEntries(MAPPING_SLOT, List.of(mappingEntry));
        hookStoreTransaction.addStorageUpdate(mappingUpdate).addStorageUpdate(slotStorageUpdate);

        // Remove the key created during crypto transfer
        if (transferKey != null) {
            final var transferKeyUpdate =
                    new LambdaStorageUpdate.LambdaStorageSlot(TestUtil.hexToBytes(transferKey), emptyValue);
            hookStoreTransaction.addStorageUpdate(transferKeyUpdate);
        }

        final var networkTransactionResponse = hookClient.executeTransactionAndRetrieveReceipt(
                hookStoreTransaction, KeyList.of(account.getPrivateKey()));

        assertThat(networkTransactionResponse)
                .extracting(NetworkTransactionResponse::getTransactionId)
                .isNotNull();

        // Clear storage tracking
        transferKey = null;
    }

    @Then("there should be no storage entry for hook")
    @RetryAsserts
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
        // Get the account to remove the hook from
        assertThat(account).extracting(ExpandedAccountId::getAccountId).isNotNull();

        // Delete the hook (storage should already be cleaned up)
        networkTransactionResponse = accountClient.updateAccount(account, updateTx -> {
            updateTx.setAccountMemo("Hook " + HOOK_ID + " removal requested");
            updateTx.addHookToDelete(HOOK_ID);
        });

        assertThat(networkTransactionResponse)
                .extracting(NetworkTransactionResponse::getReceipt)
                .isNotNull();

        // Verify the transaction was successful
        assertThat(networkTransactionResponse.getReceipt().status.toString()).isEqualTo("SUCCESS");
    }

    @Then("the account should have no hooks attached")
    public void verifyNoHooksAttached() {
        verifyMirrorTransactionsResponse(mirrorClient, HttpStatus.OK.value());
        final var accountId = account.getAccountId().toString();

        // Call the hooks API and verify the hook is marked as deleted
        final var hooksResponse = mirrorClient.getAccountHooks(accountId);
        assertThat(hooksResponse)
                .satisfies(r -> assertThat(r.getLinks()).isNotNull())
                .extracting(org.hiero.mirror.rest.model.HooksResponse::getHooks, InstanceOfAssertFactories.LIST)
                .hasSize(1)
                .first(InstanceOfAssertFactories.type(org.hiero.mirror.rest.model.Hook.class))
                .returns(true, Hook::getDeleted)
                .returns(HOOK_ID, Hook::getHookId)
                .returns(accountId, Hook::getOwnerId);
    }

    /**
     * Computes mapping storage key using the same algorithm as EVMHookHandler.processMappingEntries() This matches the
     * derivedSlot = keccak256(mappingKey, mappingSlot) implementation.
     */
    private static String computeSolidityMappingKey(byte[] entryKey, byte[] mappingSlot) {
        try {
            // Left-pad to 32 bytes, matching DomainUtils.leftPadBytes behavior
            final var mappingKeyBytes = leftPadBytes(entryKey, 32);
            final var mappingSlotBytes = leftPadBytes(mappingSlot, 32);

            // Compute derivedSlot using the same algorithm as EVMHookHandler:
            // keccak256(mappingKey, mappingSlot) where the digest is updated with key first, then digested with slot
            final var keccak = new Keccak.Digest256();
            keccak.update(mappingKeyBytes);
            final var derivedSlot = keccak.digest(mappingSlotBytes);

            // Return as hex string with proper formatting
            return TestUtil.HEX_PREFIX + Hex.encodeHexString(derivedSlot);
        } catch (Exception e) {
            log.error("Failed to compute mapping key for entry and slot: {}", e.getMessage());
            throw new RuntimeException("Failed to compute mapping storage key", e);
        }
    }
}
