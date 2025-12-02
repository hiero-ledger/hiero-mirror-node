// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.hiero.mirror.test.e2e.acceptance.client.AccountClient;
import org.hiero.mirror.test.e2e.acceptance.client.LambdaSStoreClient;
import org.hiero.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import org.hiero.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import org.springframework.http.HttpStatus;

@CustomLog
@RequiredArgsConstructor
public class LambdaStoreFeature extends AbstractFeature {

    private final AccountClient accountClient;
    private final LambdaSStoreClient lambdaSStoreClient;
    private final MirrorNodeClient mirrorClient;

    @When(
            "I create a LambdaStore transaction with key {string} and value {string} for account {string} and hook ID {long}")
    public void createLambdaStoreTransaction(String keyParam, String valueParam, String accountNameParam, long hookId) {
        // Get the account that will perform the LambdaStore operation
        var account = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountNameParam));
        assertNotNull(account);
        assertNotNull(account.getAccountId());

        // Use the dedicated LambdaSStoreClient to create and execute the transaction
        networkTransactionResponse = lambdaSStoreClient.putStorageSlot(account, hookId, keyParam, valueParam);

        assertNotNull(networkTransactionResponse, "LambdaStore transaction response should not be null");
        assertNotNull(networkTransactionResponse.getTransactionId(), "Transaction ID should not be null");
    }

    @When("I remove the LambdaStore entry with key {string} for account {string} and hook ID {long}")
    public void removeLambdaStoreEntry(String keyParam, String accountNameParam, long hookId) {
        // Get the account that will perform the LambdaStore remove operation
        var account = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountNameParam));
        assertNotNull(account);
        assertNotNull(account.getAccountId());

        // Use the dedicated LambdaSStoreClient to remove the storage slot
        var removeTransactionResponse = lambdaSStoreClient.removeStorageSlot(account, hookId, keyParam);

        assertNotNull(removeTransactionResponse, "LambdaStore remove transaction response should not be null");
        assertNotNull(removeTransactionResponse.getTransactionId(), "Remove transaction ID should not be null");
    }

    @When(
            "I create a LambdaStore mapping entry with slot {string} key {string} and value {string} for account {string} and hook ID {long}")
    public void createLambdaStoreMappingEntry(
            String mappingSlot, String entryKey, String entryValue, String accountNameParam, long hookId) {
        // Get the account that will perform the LambdaStore mapping operation
        var account = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountNameParam));
        assertNotNull(account);
        assertNotNull(account.getAccountId());

        // Use the dedicated LambdaSStoreClient to create mapping entry
        networkTransactionResponse =
                lambdaSStoreClient.putMappingEntry(account, hookId, mappingSlot, entryKey, entryValue);

        assertNotNull(networkTransactionResponse, "LambdaStore mapping transaction response should not be null");
        assertNotNull(networkTransactionResponse.getTransactionId(), "Mapping transaction ID should not be null");
    }

    @Then("the mirror node REST API should return the LambdaStore transaction")
    public void verifyLambdaStoreTransaction() {
        // Future: verify LAMBDA_SSTORE transaction in mirror node
        // Implementation will be added when mirror node supports LAMBDA_SSTORE transactions
    }

    @Then("the mirror node REST API should return LambdaStore data entries")
    public void verifyLambdaStoreDataEntries() {
        // Verify the transaction was successful
        verifyMirrorTransactionsResponse(mirrorClient, HttpStatus.OK.value());

        // TODO: Once mirror node implements LambdaStore data API endpoints, verify:
        // - Storage slot values for existing keys
        // - Empty/null values for removed keys
        // - Mapping entry values for existing mappings
        // - Empty/null values for removed mapping entries

        // For now, verify that the transaction was processed successfully
        assertNotNull(networkTransactionResponse, "Transaction response should not be null");
        assertNotNull(networkTransactionResponse.getTransactionId(), "Transaction ID should not be null");
    }

    @Then("the storage slot {string} should have value {string} for hook ID {long} from account {string}")
    public void verifyStorageSlotValue(String key, String expectedValue, long hookId, String accountName) {
        // Get the account
        var account = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountName));
        assertNotNull(account);
        String accountId = account.getAccountId().toString();

        // TODO: Once mirror node implements LambdaStore data API endpoints:
        // var storageResponse = mirrorClient.getHookStorage(accountId, hookId, key);
        // assertThat(storageResponse.getValue()).isEqualTo(expectedValue);

        // For now, verify that the transaction was processed successfully
        //        verifyMirrorTransactionsResponse(mirrorClient, HttpStatus.OK.value());
        log.info(
                "Would verify storage slot '{}' has value '{}' for hook {} on account {}",
                key,
                expectedValue,
                hookId,
                accountName);
    }

    @Then("the storage slot {string} should be empty for hook ID {long} from account {string}")
    public void verifyStorageSlotEmpty(String key, long hookId, String accountName) {
        // Get the account
        var account = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountName));
        assertNotNull(account);
        String accountId = account.getAccountId().toString();

        // TODO: Once mirror node implements LambdaStore data API endpoints:
        // var storageResponse = mirrorClient.getHookStorage(accountId, hookId, key);
        // assertThat(storageResponse).isNull() or assertThat(storageResponse.getValue()).isEmpty();

        // verifyMirrorTransactionsResponse(mirrorClient, HttpStatus.OK.value());
        log.info("Would verify storage slot '{}' is empty for hook {} on account {}", key, hookId, accountName);
    }

    @Then("the mapping {string} with key {string} should have value {string} for hook ID {long} from account {string}")
    public void verifyMappingEntryValue(
            String mappingSlot, String entryKey, String expectedValue, long hookId, String accountName) {
        // Get the account
        var account = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountName));
        assertNotNull(account);
        String accountId = account.getAccountId().toString();

        // TODO: Once mirror node implements LambdaStore data API endpoints:
        // var mappingResponse = mirrorClient.getHookMappingEntry(accountId, hookId, mappingSlot, entryKey);
        // assertThat(mappingResponse.getValue()).isEqualTo(expectedValue);

        // verifyMirrorTransactionsResponse(mirrorClient, HttpStatus.OK.value());
        log.info(
                "Would verify mapping '{}[{}]' has value '{}' for hook {} on account {}",
                mappingSlot,
                entryKey,
                expectedValue,
                hookId,
                accountName);
    }

    @Then("the mapping {string} with key {string} should be empty for hook ID {long} from account {string}")
    public void verifyMappingEntryEmpty(String mappingSlot, String entryKey, long hookId, String accountName) {
        // Get the account
        var account = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountName));
        assertNotNull(account);
        String accountId = account.getAccountId().toString();

        // TODO: Once mirror node implements LambdaStore data API endpoints:
        // var mappingResponse = mirrorClient.getHookMappingEntry(accountId, hookId, mappingSlot, entryKey);
        // assertThat(mappingResponse).isNull() or assertThat(mappingResponse.getValue()).isEmpty();

        // verifyMirrorTransactionsResponse(mirrorClient, HttpStatus.OK.value());
        log.info(
                "Would verify mapping '{}[{}]' is empty for hook {} on account {}",
                mappingSlot,
                entryKey,
                hookId,
                accountName);
    }

    @When(
            "I remove LambdaStore mapping entry with slot {string} and key {string} for hook ID {long} from account {string}")
    public void removeMappingEntry(String mappingSlot, String entryKey, long hookId, String accountName) {
        // Get the account to clean up mapping from
        var account = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountName));
        assertNotNull(account);
        assertNotNull(account.getAccountId());

        // Remove the mapping entry
        var removeMappingResponse = lambdaSStoreClient.removeMappingEntry(account, hookId, mappingSlot, entryKey);
        assertNotNull(removeMappingResponse, "Failed to remove mapping entry: " + mappingSlot + "[" + entryKey + "]");
    }

    @When("I delete hook with ID {long} from account {string}")
    public void deleteHookFromAccount(long hookIdToDelete, String accountName) {
        // Get the account to remove the hook from
        var account = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountName));
        assertNotNull(account);
        assertNotNull(account.getAccountId());

        // Delete the hook (storage should already be cleaned up)
        networkTransactionResponse = accountClient.updateAccount(account, updateTx -> {
            updateTx.setAccountMemo("Hook " + hookIdToDelete + " removal requested");
            updateTx.addHookToDelete(hookIdToDelete);
        });

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());

        // Verify the transaction was successful
        assertThat(networkTransactionResponse.getReceipt().status.toString()).isEqualTo("SUCCESS");
    }

    private void removeStorageSlots(ExpandedAccountId account, long hookId, java.util.List<String> keys) {
        // Remove specified storage slots
        for (String key : keys) {
            if (key.startsWith("mapping:")) {
                // Handle mapping entries specially
                String[] parts = key.split(":");
                if (parts.length == 3) {
                    String mappingSlot = parts[1];
                    String entryKey = parts[2];
                    var removeMappingResponse =
                            lambdaSStoreClient.removeMappingEntry(account, hookId, mappingSlot, entryKey);
                    assertNotNull(removeMappingResponse, "Failed to remove mapping entry: " + key);
                }
            } else {
                // Handle regular storage slots
                var removeResponse = lambdaSStoreClient.removeStorageSlot(account, hookId, key);
                assertNotNull(removeResponse, "Failed to remove storage slot: " + key);
            }
        }
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
}
