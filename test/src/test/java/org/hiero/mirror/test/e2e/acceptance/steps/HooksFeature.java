// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.test.e2e.acceptance.util.TestUtil.hexToBytes;
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
import java.util.ArrayList;
import java.util.List;
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
    private final List<String> storageKeys = new ArrayList<>();
    private ExpandedAccountId account;
    private long hookId;
    private String transferKey;

    @When("I attach a hook with ID {long} using existing contract to account {string}")
    public void attachHookToAccount(long hookId, String accountName) {
        this.account = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountName));
        this.hookId = hookId;
        // Get the dedicated SimpleHookContract
        final var hookContract = getContract(ContractResource.SIMPLE_HOOK);

        // Create LambdaEvmHook with the contract
        final var lambdaEvmHook = new LambdaEvmHook(hookContract.contractId());

        // Create HookCreationDetails
        final var hookCreationDetails =
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

    @Then("the mirror node REST API should return the account hook")
    @RetryAsserts
    public void verifyMirrorNodeAPIForAccountHook() {
        final var accountId = account.getAccountId().toString();

        // Call the hooks API and verify the hook exists
        final var hooksResponse = mirrorClient.getAccountHooks(accountId);

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
                    assertThat(hook.getHookId()).isEqualTo(hookId);
                });
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
                account, recipient.getAccountId(), hbarAmount, hookId, account.getPrivateKey());

        assertThat(networkTransactionResponse)
                .isNotNull()
                .extracting(NetworkTransactionResponse::getTransactionId)
                .isNotNull();
    }

    @When("I create a HookStore transaction with slot pair {string} and mapping triple {string}")
    public void createHookStoreTransactionWithPairAndTriple(String slotPair, String mappingTriple) {
        final var hookStoreTransaction = hookClient.getLambdaSStoreTransaction(account, hookId);

        // Parse slot pair "0x01:0x02" (key:value)
        String[] slotParts = slotPair.split(":");
        final var slotKey = hexToBytes(slotParts[0]);
        final var slotValue = hexToBytes(slotParts[1]);

        // Parse mapping triple "0x03:0x04:0x05" (slot:key:value)
        String[] mappingParts = mappingTriple.split(":");
        final var mappingSlot = hexToBytes(mappingParts[0]);
        final var mappingKey = hexToBytes(mappingParts[1]);
        final var mappingValue = hexToBytes(mappingParts[2]);

        // Create and add storage updates
        final var slotStorageUpdate = new LambdaStorageUpdate.LambdaStorageSlot(slotKey, slotValue);
        final var mappingEntry = LambdaMappingEntry.ofKey(mappingKey, mappingValue);
        final var mappingUpdate = new LambdaStorageUpdate.LambdaMappingEntries(mappingSlot, List.of(mappingEntry));
        hookStoreTransaction.addStorageUpdate(slotStorageUpdate);
        hookStoreTransaction.addStorageUpdate(mappingUpdate);

        // Execute transaction
        final var networkTransactionResponse = hookClient.executeTransactionAndRetrieveReceipt(
                hookStoreTransaction, KeyList.of(account.getPrivateKey()));

        assertThat(networkTransactionResponse)
                .isNotNull()
                .extracting(NetworkTransactionResponse::getTransactionId)
                .isNotNull();

        // Store formatted keys for later verification
        final var keyBytes = leftPadBytes(slotKey, 32);
        final var formattedKey = TestUtil.HEX_PREFIX + Hex.encodeHexString(keyBytes);
        final var formattedKey1 = computeSolidityMappingKey(mappingKey, mappingSlot);

        storageKeys.add(formattedKey);
        storageKeys.add(formattedKey1);
    }

    @Then("the mirror node REST API should return hook storage entries")
    @RetryAsserts
    public void verifyMirrorNodeAPIForAccountHookStorage() {
        final var accountId = account.getAccountId().toString();
        // Call the hook storage API endpoint and verify execution data
        HooksStorageResponse hookStorageResponse = mirrorClient.getHookStorage(accountId, hookId);

        assertThat(hookStorageResponse).isNotNull().satisfies(response -> {
            assertThat(response.getHookId()).isEqualTo(hookId);
            assertThat(response.getLinks()).isNotNull();
            assertThat(response.getStorage()).isNotNull();
            assertThat(response.getStorage().size()).isGreaterThan(0);
        });

        // Capture the transfer key created by crypto transfer (should be the first entry not in our storageKeys)
        for (var entry : hookStorageResponse.getStorage()) {
            if (!storageKeys.contains(entry.getKey())) {
                transferKey = entry.getKey();
                break;
            }
        }
    }

    @When("I remove storage for slot pair {string} and mapping triple {string} along with transfer key")
    public void removeStorage(String slotPair, String mappingTriple) {
        // Get the account that will perform the LambdaStore operation

        final var emptyValue = new byte[0];
        final var hookStoreTransaction = hookClient.getLambdaSStoreTransaction(account, hookId);

        // Parse slot pair "0x01:0x02" (key:value)
        String[] slotParts = slotPair.split(":");
        final var slotKey = hexToBytes(slotParts[0]);

        // Parse mapping triple "0x03:0x04:0x05" (slot:key:value)
        String[] mappingParts = mappingTriple.split(":");
        final var mappingSlot = hexToBytes(mappingParts[0]);
        final var mappingKey = hexToBytes(mappingParts[1]);

        // Create and add storage updates
        final var slotStorageUpdate = new LambdaStorageUpdate.LambdaStorageSlot(slotKey, emptyValue);
        final var mappingEntry = LambdaMappingEntry.ofKey(mappingKey, emptyValue);
        final var mappingUpdate = new LambdaStorageUpdate.LambdaMappingEntries(mappingSlot, List.of(mappingEntry));
        hookStoreTransaction.addStorageUpdate(slotStorageUpdate);
        hookStoreTransaction.addStorageUpdate(mappingUpdate);

        // Remove the key created during crypto transfer
        if (transferKey != null) {
            final var transferKeyUpdate =
                    new LambdaStorageUpdate.LambdaStorageSlot(TestUtil.hexToBytes(transferKey), emptyValue);
            hookStoreTransaction.addStorageUpdate(transferKeyUpdate);
        }

        final var networkTransactionResponse = hookClient.executeTransactionAndRetrieveReceipt(
                hookStoreTransaction, KeyList.of(account.getPrivateKey()));

        assertThat(networkTransactionResponse)
                .isNotNull()
                .extracting(NetworkTransactionResponse::getTransactionId)
                .isNotNull();

        // Clear storage tracking
        storageKeys.clear();
        transferKey = null;
    }

    @Then("there should be no storage entry for hook")
    @RetryAsserts
    public void verifyEmptyStorageForAccount() {

        final var storageResponse =
                mirrorClient.getHookStorage(account.getAccountId().toString(), hookId);
        assertThat(storageResponse).isNotNull().satisfies(response -> {
            assertThat(response.getHookId()).isEqualTo(hookId);
            assertThat(response.getStorage()).isEmpty();
        });
    }

    @When("I delete hook")
    public void deleteHookFromAccount() {
        // Get the account to remove the hook from
        assertThat(account)
                .isNotNull()
                .extracting(ExpandedAccountId::getAccountId)
                .isNotNull();

        // Delete the hook (storage should already be cleaned up)
        networkTransactionResponse = accountClient.updateAccount(account, updateTx -> {
            updateTx.setAccountMemo("Hook " + hookId + " removal requested");
            updateTx.addHookToDelete(hookId);
        });

        assertThat(networkTransactionResponse)
                .isNotNull()
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
