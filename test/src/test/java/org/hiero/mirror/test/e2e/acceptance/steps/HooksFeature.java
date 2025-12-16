// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.hexToBytes;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.leftPadBytes;

import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.HookCreationDetails;
import com.hedera.hashgraph.sdk.HookExtensionPoint;
import com.hedera.hashgraph.sdk.LambdaEvmHook;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.binary.Hex;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.bouncycastle.jcajce.provider.digest.Keccak;
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

    private final AccountClient accountClient;
    private final HookClient hookClient;
    private final MirrorNodeClient mirrorClient;

    @When("I attach a hook with ID {long} using existing contract to account {string}")
    public void attachHookToAccount(long hookId, String accountName) {
        var account = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountName));
        assertThat(account).isNotNull();
        assertThat(account.getAccountId()).isNotNull();

        // Get the EstimateGasContract for the hook
        var estimateGasContract = getContract(ContractResource.ESTIMATE_GAS);

        // Create LambdaEvmHook with the contract
        LambdaEvmHook lambdaEvmHook = new LambdaEvmHook(estimateGasContract.contractId());

        // Create HookCreationDetails
        HookCreationDetails hookCreationDetails =
                new HookCreationDetails(HookExtensionPoint.ACCOUNT_ALLOWANCE_HOOK, hookId, lambdaEvmHook);

        // Attach hook to account using AccountUpdateTransaction
        networkTransactionResponse = accountClient.updateAccount(account, updateTx -> {
            updateTx.addHookToCreate(hookCreationDetails);
        });
        assertThat(networkTransactionResponse)
                .isNotNull()
                .extracting(NetworkTransactionResponse::getTransactionId)
                .isNotNull();
    }

    @Then("the mirror node REST API should return the account hooks for {string}")
    @RetryAsserts
    public void theMirrorNodeRESTAPIShouldReturnTheAccountHooksFor(String accountName) {
        // Get the account from the most recent operation - assuming ALICE from feature file
        var account = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountName));
        String accountId = account.getAccountId().toString();

        // Call the hooks API and verify the hook exists
        var hooksResponse = mirrorClient.getAccountHooks(accountId);

        assertThat(hooksResponse)
                .isNotNull()
                .satisfies(r -> assertThat(r.getLinks()).isNotNull())
                .extracting(org.hiero.mirror.rest.model.HooksResponse::getHooks)
                .isNotNull()
                .asInstanceOf(InstanceOfAssertFactories.LIST)
                .isNotEmpty()
                .first()
                .asInstanceOf(InstanceOfAssertFactories.type(org.hiero.mirror.rest.model.Hook.class))
                .satisfies(hook -> {
                    assertThat(hook.getDeleted()).isFalse(); // Hook should not be deleted
                    assertThat(hook.getOwnerId()).isEqualTo(accountId);
                    assertThat(hook.getHookId()).isNotNull();
                });
    }

    @When("I trigger hook execution via crypto transfer from {string} of {long} tℏ with hook {long}")
    public void triggerHookExecutionViaCryptoTransfer(String accountName, long transferAmountInTinybar, long hookId) {
        // Use the operator account as sender and specified account as recipient (like
        // TransferTransactionHooksIntegrationTest)
        var recipient = accountClient.getAccount(AccountClient.AccountNameEnum.OPERATOR);
        var sender = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountName));

        // Convert tinybar to Hbar
        var hbarAmount = Hbar.fromTinybars(transferAmountInTinybar);

        // Execute crypto transfer with hook using the pattern from TransferTransactionHooksIntegrationTest
        networkTransactionResponse = hookClient.sendCryptoTransferWithHook(
                sender, recipient.getAccountId(), hbarAmount, hookId, sender.getPrivateKey());

        assertThat(networkTransactionResponse)
                .isNotNull()
                .extracting(NetworkTransactionResponse::getTransactionId)
                .isNotNull();
    }

    @Then("the mirror node REST API should return hook storage entries for account {string} and {long}")
    @RetryAsserts
    public void theMirrorNodeRESTAPIShouldReturnHookStorageEntriesForAccountAndHookId(String accountName, long hookId) {
        // Get the account that has the hook
        var account = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountName));
        String accountId = account.getAccountId().toString();
        // Call the hook storage API endpoint and verify execution data
        HooksStorageResponse hookStorageResponse = mirrorClient.getHookStorage(accountId, hookId, null);

        assertThat(hookStorageResponse).isNotNull().satisfies(response -> {
            assertThat(response.getHookId()).isEqualTo(hookId);
            assertThat(response.getLinks()).isNotNull();
            assertThat(response.getStorage()).isNotNull();
            assertThat(response.getStorage().size()).isGreaterThan(0);
        });
    }

    @When(
            "I create a LambdaStore transaction with key {string} and value {string} for account {string} and hook ID {long}")
    public void createLambdaStoreTransaction(String keyParam, String valueParam, String accountNameParam, long hookId) {
        // Get the account that will perform the LambdaStore operation
        var account = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountNameParam));
        assertThat(account)
                .isNotNull()
                .extracting(ExpandedAccountId::getAccountId)
                .isNotNull();

        // Convert hex strings to 256-bit byte arrays
        byte[] keyBytes = hexToBytes(keyParam);
        byte[] valueBytes = hexToBytes(valueParam);

        // Use the dedicated HookClient to create and execute the transaction
        networkTransactionResponse = hookClient.putStorageSlot(account, hookId, keyBytes, valueBytes);

        assertThat(networkTransactionResponse)
                .isNotNull()
                .extracting(NetworkTransactionResponse::getTransactionId)
                .isNotNull();
    }

    @Then("the storage slot {string} should have value {string} for hook ID {long} from account {string}")
    @RetryAsserts
    public void verifyStorageSlotValue(String key, String expectedValue, long hookId, String accountName) {
        // Get the account
        var account = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountName));
        assertThat(account)
                .isNotNull()
                .extracting(ExpandedAccountId::getAccountId)
                .isNotNull();
        String accountId = account.getAccountId().toString();

        // Convert hex string to proper 32-byte padded hex format for API call
        byte[] keyBytes = leftPadBytes(hexToBytes(key), 32);
        String formattedKey = TestUtil.HEX_PREFIX + Hex.encodeHexString(keyBytes);
        var storageResponse = mirrorClient.getHookStorage(accountId, hookId, formattedKey);

        // Verify that the storage response contains the expected key and value
        assertThat(storageResponse).isNotNull().satisfies(response -> {
            assertThat(response.getHookId()).isEqualTo(hookId);
            assertThat(response.getStorage()).isNotNull().isNotEmpty();
            // Find the storage entry that matches our key
            var matchingEntry = response.getStorage().stream()
                    .filter(entry -> formattedKey.equals(entry.getKey()))
                    .findFirst()
                    .orElse(null);
            assertThat(matchingEntry).isNotNull();
            // Convert expected hex value to 32-byte padded hex format for comparison
            byte[] expectedValueBytes = leftPadBytes(hexToBytes(expectedValue), 32);
            String expectedHexValue = Hex.encodeHexString(expectedValueBytes);
            assertThat(matchingEntry.getValue().substring(2)).isEqualTo(expectedHexValue);
        });
    }

    @When("I remove the LambdaStore entry with key {string} for account {string} and hook ID {long}")
    public void removeLambdaStoreEntry(String keyParam, String accountNameParam, long hookId) {
        // Get the account that will perform the LambdaStore remove operation
        var account = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountNameParam));
        assertThat(account)
                .isNotNull()
                .extracting(ExpandedAccountId::getAccountId)
                .isNotNull();

        // Convert hex string to byte array and use empty byte array for removal
        byte[] keyBytes = hexToBytes(keyParam);
        byte[] emptyValue = new byte[0];

        // Remove storage slot by setting empty value
        networkTransactionResponse = hookClient.putStorageSlot(account, hookId, keyBytes, emptyValue);

        assertThat(networkTransactionResponse)
                .isNotNull()
                .extracting(NetworkTransactionResponse::getTransactionId)
                .isNotNull();
    }

    @Then("the storage slot {string} should be empty for hook ID {long} from account {string}")
    @RetryAsserts
    public void verifyStorageSlotEmpty(String key, long hookId, String accountName) {
        // Get the account
        var account = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountName));
        assertThat(account)
                .isNotNull()
                .extracting(ExpandedAccountId::getAccountId)
                .isNotNull();
        String accountId = account.getAccountId().toString();

        // Convert hex string to proper 32-byte padded hex format for API call
        byte[] keyBytes = hexToBytes(key);
        String formattedKey = TestUtil.HEX_PREFIX + Hex.encodeHexString(keyBytes);
        // Call hook storage API and verify the key is empty/removed
        var storageResponse = mirrorClient.getHookStorage(accountId, hookId, formattedKey);
        assertThat(storageResponse).isNotNull();
        assertThat(storageResponse.getStorage())
                .satisfiesAnyOf(
                        storage -> assertThat(storage).isEmpty(),
                        storage -> storage.forEach(
                                entry -> assertThat(entry.getValue()).isNullOrEmpty()));

        log.info("Verified storage slot '{}' is empty for hook {} on account {}", key, hookId, accountName);
    }

    @When(
            "I create a LambdaStore mapping entry with slot {string} key {string} and value {string} for account {string} and hook ID {long}")
    public void createLambdaStoreMappingEntry(
            String mappingSlot, String entryKey, String entryValue, String accountNameParam, long hookId) {
        // Get the account that will perform the LambdaStore mapping operation
        var account = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountNameParam));
        assertThat(account)
                .isNotNull()
                .extracting(ExpandedAccountId::getAccountId)
                .isNotNull();

        // Convert hex strings to byte arrays
        byte[] mappingSlotBytes = hexToBytes(mappingSlot);
        byte[] entryKeyBytes = hexToBytes(entryKey);
        byte[] entryValueBytes = hexToBytes(entryValue);

        // Use the dedicated HookClient to create mapping entry
        networkTransactionResponse =
                hookClient.putMappingEntry(account, hookId, mappingSlotBytes, entryKeyBytes, entryValueBytes);

        assertThat(networkTransactionResponse)
                .isNotNull()
                .extracting(NetworkTransactionResponse::getTransactionId)
                .isNotNull();
    }

    @Then("the mapping {string} with key {string} should have value {string} for hook ID {long} from account {string}")
    @RetryAsserts
    public void verifyMappingEntryValue(
            String mappingSlot, String entryKey, String expectedValue, long hookId, String accountName) {
        // Get the account
        var account = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountName));
        assertThat(account)
                .isNotNull()
                .extracting(ExpandedAccountId::getAccountId)
                .isNotNull();
        String accountId = account.getAccountId().toString();

        // Compute the mapping storage key using keccak256(abi.encode(key, slot))
        // This follows Solidity storage layout for mappings
        String formattedMappingKey = computeSolidityMappingKey(hexToBytes(entryKey), hexToBytes(mappingSlot));

        // Query for the specific mapping entry by its computed key
        var storageResponse = mirrorClient.getHookStorage(accountId, hookId, formattedMappingKey);

        // Verify that the storage response contains the expected mapping entry
        assertThat(storageResponse).isNotNull().satisfies(response -> {
            assertThat(response.getHookId()).isEqualTo(hookId);
            assertThat(response.getStorage()).isNotNull().hasSize(1);

            // Since we queried for a specific key, there should be exactly one entry
            var storageEntry = response.getStorage().get(0);
            assertThat(storageEntry.getKey()).isEqualTo(formattedMappingKey);

            // Convert expected hex value to 32-byte padded hex format for comparison
            byte[] expectedValueBytes = leftPadBytes(hexToBytes(expectedValue), 32);
            String expectedHexValue = TestUtil.HEX_PREFIX + Hex.encodeHexString(expectedValueBytes);
            assertThat(storageEntry.getValue()).isEqualTo(expectedHexValue);
        });
    }

    @When(
            "I remove LambdaStore mapping entry with slot {string} and key {string} for hook ID {long} from account {string}")
    public void removeMappingEntry(String mappingSlot, String entryKey, long hookId, String accountName) {
        // Get the account to clean up mapping from
        var account = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountName));
        assertThat(account)
                .isNotNull()
                .extracting(ExpandedAccountId::getAccountId)
                .isNotNull();

        // Convert hex strings to byte arrays
        byte[] mappingSlotBytes = hexToBytes(mappingSlot);
        byte[] entryKeyBytes = hexToBytes(entryKey);
        byte[] emptyValue = new byte[0];

        // Remove mapping entry by setting empty value
        networkTransactionResponse =
                hookClient.putMappingEntry(account, hookId, mappingSlotBytes, entryKeyBytes, emptyValue);
        assertThat(networkTransactionResponse)
                .isNotNull()
                .extracting(NetworkTransactionResponse::getTransactionId)
                .isNotNull();
    }

    @Then("the mapping {string} with key {string} should be empty for hook ID {long} from account {string}")
    @RetryAsserts
    public void verifyMappingEntryEmpty(String mappingSlot, String entryKey, long hookId, String accountName) {
        // Get the account
        var account = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountName));
        assertThat(account)
                .isNotNull()
                .extracting(ExpandedAccountId::getAccountId)
                .isNotNull();
        String accountId = account.getAccountId().toString();

        // Compute the mapping storage key using keccak256(abi.encode(key, slot))
        // This follows the same pattern used in verifyMappingEntryValue method
        String formattedMappingKey = computeSolidityMappingKey(hexToBytes(entryKey), hexToBytes(mappingSlot));

        // Query for the specific mapping entry by its computed key to check if it's been removed
        var storageResponse = mirrorClient.getHookStorage(accountId, hookId, formattedMappingKey);

        // Verify that the mapping entry is not present or has empty/zero value
        assertThat(storageResponse).isNotNull().satisfies(response -> {
            assertThat(response.getHookId()).isEqualTo(hookId);
            // Since we queried for a specific key, expect either 0 entries (removed) or 1 entry (empty value)
            assertThat(response.getStorage())
                    .satisfiesAnyOf(
                            // Case 1: No storage entries (mapping was completely removed)
                            storage -> assertThat(storage).isEmpty(),
                            // Case 2: Exactly one entry with empty/zero value
                            storage -> {
                                assertThat(storage).hasSize(1);
                                var storageEntry = storage.get(0);
                                assertThat(storageEntry.getKey()).isEqualTo(formattedMappingKey);
                                assertThat(storageEntry.getValue())
                                        .satisfiesAnyOf(v -> assertThat(v).isNullOrEmpty(), v -> assertThat(v)
                                                .isEqualTo("0x" + "0".repeat(64)));
                            });
        });
    }

    @When("I delete hook with ID {long} from account {string}")
    public void deleteHookFromAccount(long hookIdToDelete, String accountName) {
        // Get the account to remove the hook from
        var account = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountName));
        assertThat(account)
                .isNotNull()
                .extracting(ExpandedAccountId::getAccountId)
                .isNotNull();

        // Delete the hook (storage should already be cleaned up)
        networkTransactionResponse = accountClient.updateAccount(account, updateTx -> {
            updateTx.setAccountMemo("Hook " + hookIdToDelete + " removal requested");
            updateTx.addHookToDelete(hookIdToDelete);
        });

        assertThat(networkTransactionResponse)
                .isNotNull()
                .extracting(NetworkTransactionResponse::getReceipt)
                .isNotNull();

        // Verify the transaction was successful
        assertThat(networkTransactionResponse.getReceipt().status.toString()).isEqualTo("SUCCESS");
    }

    @Then("the account {string} should have no hooks attached")
    public void verifyNoHooksAttached(String accountNameParam) {
        verifyMirrorTransactionsResponse(mirrorClient, HttpStatus.OK.value());
        // Get the account from the most recent operation
        var account = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountNameParam));
        String accountId = account.getAccountId().toString();

        // Call the hooks API and verify the hook is marked as deleted
        var hooksResponse = mirrorClient.getAccountHooks(accountId);

        assertThat(hooksResponse)
                .isNotNull()
                .satisfies(r -> assertThat(r.getLinks()).isNotNull())
                .extracting(org.hiero.mirror.rest.model.HooksResponse::getHooks)
                .isNotNull()
                .asInstanceOf(InstanceOfAssertFactories.LIST)
                .isNotEmpty()
                .hasSize(1)
                .first()
                .asInstanceOf(InstanceOfAssertFactories.type(org.hiero.mirror.rest.model.Hook.class))
                .satisfies(hook -> {
                    assertThat(hook.getDeleted()).isTrue(); // Hook should be marked as deleted
                    assertThat(hook.getOwnerId()).isEqualTo(accountId);
                });
    }

    /**
     * Computes mapping storage key using the same algorithm as EVMHookHandler.processMappingEntries() This matches the
     * derivedSlot = keccak256(mappingKey, mappingSlot) implementation.
     */
    private String computeSolidityMappingKey(byte[] entryKey, byte[] mappingSlot) {
        try {
            // Left-pad to 32 bytes, matching DomainUtils.leftPadBytes behavior
            byte[] mappingKeyBytes = leftPadBytes(entryKey, 32);
            byte[] mappingSlotBytes = leftPadBytes(mappingSlot, 32);

            // Compute derivedSlot using the same algorithm as EVMHookHandler:
            // keccak256(mappingKey, mappingSlot) where the digest is updated with key first, then digested with slot
            var keccak = new Keccak.Digest256();
            keccak.update(mappingKeyBytes);
            byte[] derivedSlot = keccak.digest(mappingSlotBytes);

            // Return as hex string with proper formatting
            return TestUtil.HEX_PREFIX + Hex.encodeHexString(derivedSlot);
        } catch (Exception e) {
            log.error("Failed to compute mapping key for entry and slot: {}", e.getMessage());
            throw new RuntimeException("Failed to compute mapping storage key", e);
        }
    }
}
