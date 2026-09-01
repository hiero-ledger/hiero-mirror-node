// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.common.util.DomainUtils.bytesToHex;
import static org.hiero.mirror.common.util.DomainUtils.toEvmAddress;
import static org.hiero.mirror.web3.utils.ByteUtils.wrapToWordSize;

import com.google.common.collect.Range;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.hiero.mirror.web3.Web3Properties;
import org.hiero.mirror.web3.common.TransactionHashParameter;
import org.hiero.mirror.web3.exception.EntityNotFoundException;
import org.hiero.mirror.web3.service.model.PrestateRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@RequiredArgsConstructor
final class PrestateServiceTest extends Web3IntegrationTest {

    private static final byte[] RUNTIME_BYTECODE = new byte[] {0x60, 0x40};
    private static final byte[] STORAGE_SLOT =
            new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};
    private static final byte[] VALUE_READ = new byte[] {0x14};
    private static final byte[] VALUE_WRITTEN = new byte[] {0x28};
    private static final int DEFAULT_MAX_TOUCHED_ACCOUNTS = 1000;

    private final PrestateService prestateService;

    @Resource
    private Web3Properties web3Properties;

    @AfterEach
    void tearDown() {
        web3Properties.setMaxTouchedAccounts(DEFAULT_MAX_TOUCHED_ACCOUNTS);
    }

    @Test
    void callWithDiffEnabledReturnsBothPreAndPost() {
        final var fixture = persistTransferFixture(true, 50L);
        persistTreasuryBalance(fixture.createdTimestamp());
        persistAccountBalance(fixture.accountId(), fixture.createdTimestamp(), 100L);

        final var response = prestateService.processPrestateCall(createRequest(fixture.hash(), true, false, false));

        assertThat(response.getPre()).hasSize(1);
        assertThat(response.getPost()).hasSize(1);
        assertThat(response.getPre().getFirst().getBalance()).isEqualTo("0xe8d4a51000");
        assertThat(response.getPost().getFirst().getBalance()).isEqualTo("0x15d3ef79800");
        assertThat(response.getPost().getFirst().getNonce()).isEqualTo(2L);
    }

    @Test
    void callWithDiffDisabledReturnsOnlyPre() {
        final var fixture = persistTransferFixture(false, 0L);
        persistTreasuryBalance(fixture.createdTimestamp());
        persistAccountBalance(fixture.accountId(), fixture.createdTimestamp(), 100L);

        final var response = prestateService.processPrestateCall(createRequest(fixture.hash(), false, false, false));

        assertThat(response.getPre()).hasSize(1);
        assertThat(response.getPost()).isNullOrEmpty();
    }

    @Test
    void callWithDiffEnabledExcludesUnchangedEntries() {
        final var fixture = persistTransferFixture(false, 0L);
        persistTreasuryBalance(fixture.createdTimestamp());
        persistAccountBalance(fixture.accountId(), fixture.createdTimestamp(), 100L);

        final var response = prestateService.processPrestateCall(createRequest(fixture.hash(), true, false, false));

        assertThat(response.getPre()).isEmpty();
        assertThat(response.getPost()).isEmpty();
    }

    @Test
    void callWithCodeEnabled() {
        final var fixture = persistContractFixture(RUNTIME_BYTECODE);

        final var response = prestateService.processPrestateCall(createRequest(fixture.hash(), false, true, false));

        assertThat(response.getPre()).hasSize(1);
        assertThat(response.getPre().getFirst().getCode()).isEqualTo(wrapToWordSize(RUNTIME_BYTECODE));
    }

    @Test
    void callWithStorageEnabled() {
        final var fixture = persistContractFixture(null);
        domainBuilder
                .contractStateChange()
                .customize(c -> c.consensusTimestamp(fixture.consensusTimestamp())
                        .contractId(fixture.contractId().getId())
                        .slot(STORAGE_SLOT)
                        .valueRead(VALUE_READ)
                        .valueWritten(null))
                .persist();

        final var response = prestateService.processPrestateCall(createRequest(fixture.hash(), false, false, true));

        assertThat(response.getPre()).hasSize(1);
        assertThat(response.getPre().getFirst().getStorage())
                .containsEntry(wrapToWordSize(STORAGE_SLOT), wrapToWordSize(VALUE_READ));
    }

    @Test
    void callWithDiffAndStorageEnabledPopulatesPreAndPostStorageFromStateChanges() {
        final var fixture = persistContractFixture(null);
        persistTreasuryBalance(fixture.createdTimestamp());
        persistAccountBalance(fixture.contractId(), fixture.createdTimestamp(), 50L);
        domainBuilder
                .contractStateChange()
                .customize(c -> c.consensusTimestamp(fixture.consensusTimestamp())
                        .contractId(fixture.contractId().getId())
                        .slot(STORAGE_SLOT)
                        .valueRead(VALUE_READ)
                        .valueWritten(VALUE_WRITTEN))
                .persist();

        final var response = prestateService.processPrestateCall(createRequest(fixture.hash(), true, false, true));

        assertThat(response.getPre()).hasSize(1);
        assertThat(response.getPost()).hasSize(1);
        assertThat(response.getPre().getFirst().getStorage())
                .containsEntry(wrapToWordSize(STORAGE_SLOT), wrapToWordSize(VALUE_READ));
        assertThat(response.getPost().getFirst().getStorage())
                .containsEntry(wrapToWordSize(STORAGE_SLOT), wrapToWordSize(VALUE_WRITTEN));
    }

    @Test
    void callWithContractTransactionHashNotFound() {
        final var hash = domainBuilder.bytes(32);
        final var request = createRequest(hash, false, false, false);

        assertThatThrownBy(() -> prestateService.processPrestateCall(request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Contract transaction hash not found");
    }

    @Test
    void callWithDiffEnabledIncludesOnlyChangedEntriesInPreAndPost() {
        final var payerId = domainBuilder.entityId();
        final var contractId = domainBuilder.entityId();
        final var changedAccount = domainBuilder.entityId();
        final var unchangedAccount = domainBuilder.entityId();
        final var createdTimestamp = domainBuilder.timestamp();
        final var consensusTimestamp = createdTimestamp + 100;
        final var hash = domainBuilder.bytes(32);

        persistBareEntity(changedAccount, EntityType.ACCOUNT, 1L, createdTimestamp);
        persistBareEntity(unchangedAccount, EntityType.ACCOUNT, 2L, createdTimestamp);
        persistTreasuryBalance(createdTimestamp);
        persistAccountBalance(changedAccount, createdTimestamp, 100L);
        persistAccountBalance(unchangedAccount, createdTimestamp, 200L);

        persistContractTransactionHash(hash, consensusTimestamp, payerId, contractId);
        domainBuilder
                .contractAction()
                .customize(a -> a.consensusTimestamp(consensusTimestamp)
                        .caller(contractId)
                        .callerType(EntityType.CONTRACT)
                        .recipientAccount(changedAccount)
                        .value(50L)
                        .index(0))
                .persist();
        domainBuilder
                .contractAction()
                .customize(a -> a.consensusTimestamp(consensusTimestamp)
                        .caller(contractId)
                        .callerType(EntityType.CONTRACT)
                        .recipientAccount(unchangedAccount)
                        .value(0L)
                        .index(1))
                .persist();

        final var response = prestateService.processPrestateCall(createRequest(hash, true, false, false));

        assertThat(response.getPre()).hasSize(1);
        assertThat(response.getPost()).hasSize(1);
        assertThat(response.getPre().getFirst().getAddress()).isEqualTo(toLongZeroAddress(changedAccount));
        assertThat(response.getPost().getFirst().getBalance()).isEqualTo("0x15d3ef79800");
    }

    @Test
    void callWithoutCodeStillIncludesContractWithBalanceAndNonce() {
        final var fixture = persistContractFixture(null);
        persistTreasuryBalance(fixture.createdTimestamp());
        persistAccountBalance(fixture.contractId(), fixture.createdTimestamp(), 50L);

        final var response = prestateService.processPrestateCall(createRequest(fixture.hash(), false, false, false));

        assertThat(response.getPre()).hasSize(1);
        assertThat(response.getPre().getFirst().getAddress()).isEqualTo(toLongZeroAddress(fixture.contractId()));
        assertThat(response.getPre().getFirst().getBalance()).isEqualTo("0x746a528800");
        assertThat(response.getPre().getFirst().getNonce()).isEqualTo(3L);
        assertThat(response.getPre().getFirst().getCode()).isNull();
        assertThat(response.getPre().getFirst().getStorage()).isNullOrEmpty();
    }

    @Test
    void callWithCodeLoadsBytecodeByIdsAndTimestamp() {
        final var fixture = persistContractFixture(RUNTIME_BYTECODE);

        final var response = prestateService.processPrestateCall(createRequest(fixture.hash(), false, true, false));

        assertThat(response.getPre()).hasSize(1);
        assertThat(response.getPre().getFirst().getAddress()).isEqualTo(toLongZeroAddress(fixture.contractId()));
        assertThat(response.getPre().getFirst().getCode()).isEqualTo(wrapToWordSize(RUNTIME_BYTECODE));
    }

    @Test
    void callWithDiffAndCodePopulatesPreAndPostBytecode() {
        final var fixture = persistContractFixtureWithAction(RUNTIME_BYTECODE, 10L);
        persistTreasuryBalance(fixture.createdTimestamp());
        persistAccountBalance(fixture.contractId(), fixture.createdTimestamp(), 50L);

        final var response = prestateService.processPrestateCall(createRequest(fixture.hash(), true, true, false));

        assertThat(response.getPre()).hasSize(1);
        assertThat(response.getPre().getFirst().getCode()).isEqualTo(wrapToWordSize(RUNTIME_BYTECODE));
        assertThat(response.getPost()).hasSize(1);
        assertThat(response.getPost().getFirst().getCode()).isEqualTo(wrapToWordSize(RUNTIME_BYTECODE));
    }

    @Test
    void callWithDiffIncludesNewlyCreatedAccountWithEmptyPreEntry() {
        final var payerId = domainBuilder.entityId();
        final var contractId = domainBuilder.entityId();
        final var existingAccount = domainBuilder.entityId();
        final var newlyCreatedAccount = domainBuilder.entityId();
        final var createdTimestamp = domainBuilder.timestamp();
        final var consensusTimestamp = createdTimestamp + 100;
        final var hash = domainBuilder.bytes(32);

        // Existing account - created before the transaction
        persistBareEntity(existingAccount, EntityType.ACCOUNT, 1L, createdTimestamp);
        persistTreasuryBalance(createdTimestamp);
        persistAccountBalance(existingAccount, createdTimestamp, 100L);

        // Newly created account - created during the transaction (timestamp = consensusTimestamp)
        persistBareEntity(newlyCreatedAccount, EntityType.ACCOUNT, 0L, consensusTimestamp);

        persistContractTransactionHash(hash, consensusTimestamp, payerId, contractId);
        // Action with existing account that receives a transfer
        domainBuilder
                .contractAction()
                .customize(a -> a.consensusTimestamp(consensusTimestamp)
                        .caller(contractId)
                        .callerType(EntityType.CONTRACT)
                        .recipientAccount(existingAccount)
                        .value(50L)
                        .index(0))
                .persist();
        // Action with newly created account
        domainBuilder
                .contractAction()
                .customize(a -> a.consensusTimestamp(consensusTimestamp)
                        .caller(contractId)
                        .callerType(EntityType.CONTRACT)
                        .recipientAccount(newlyCreatedAccount)
                        .value(25L)
                        .index(1))
                .persist();

        final var response = prestateService.processPrestateCall(createRequest(hash, true, false, false));

        // Should have 2 entries in both pre and post
        assertThat(response.getPre()).hasSize(2);
        assertThat(response.getPost()).hasSize(2);

        // Find the newly created account in responses
        final var newAccountAddress = toLongZeroAddress(newlyCreatedAccount);
        final var newAccountPre = response.getPre().stream()
                .filter(t -> newAccountAddress.equals(t.getAddress()))
                .findFirst()
                .orElse(null);
        final var newAccountPost = response.getPost().stream()
                .filter(t -> newAccountAddress.equals(t.getAddress()))
                .findFirst()
                .orElse(null);

        // Pre entry for newly created account should be empty (no balance, no nonce)
        assertThat(newAccountPre).isNotNull();
        assertThat(newAccountPre.getBalance()).isNull();
        assertThat(newAccountPre.getNonce()).isNull();

        // Post entry for newly created account should have data
        assertThat(newAccountPost).isNotNull();
        assertThat(newAccountPost.getBalance()).isEqualTo("0x3a35294400"); // 25 tinybars in weibars
        assertThat(newAccountPost.getNonce()).isEqualTo(0L);
    }

    @ParameterizedTest
    @CsvSource({
        "true, true, true",
        "false, true, true",
        "true, false, true",
        "true, true, false",
        "false, false, true",
        "false, true, false",
        "true, false, false",
        "false, false, false"
    })
    void callWithDifferentCombinationsOfFlags(final boolean diff, final boolean code, final boolean storage) {
        final var fixture = persistTransferFixture(false, diff ? 1L : 0L);
        persistTreasuryBalance(fixture.createdTimestamp());
        persistAccountBalance(fixture.accountId(), fixture.createdTimestamp(), 100L);

        final var response = prestateService.processPrestateCall(createRequest(fixture.hash(), diff, code, storage));

        assertThat(response.getPre()).isNotNull();
        if (diff) {
            assertThat(response.getPost()).isNotNull();
        } else {
            assertThat(response.getPost()).isNullOrEmpty();
        }
    }

    @Test
    void callReturnsAllAccountsWhenBelowMaxLimit() {
        web3Properties.setMaxTouchedAccounts(10);
        final var fixture = persistMultipleAccountsFixture(5);

        final var response = prestateService.processPrestateCall(createRequest(fixture.hash(), false, false, false));

        assertThat(response.getPre()).hasSize(5);
    }

    @Test
    void callReturnsAllAccountsWhenExactlyAtMaxLimit() {
        web3Properties.setMaxTouchedAccounts(5);
        final var fixture = persistMultipleAccountsFixture(5);

        final var response = prestateService.processPrestateCall(createRequest(fixture.hash(), false, false, false));

        assertThat(response.getPre()).hasSize(5);
    }

    private PrestateRequest createRequest(
            final byte[] hash, final boolean diff, final boolean code, final boolean storage) {
        return new PrestateRequest(
                new TransactionHashParameter(org.apache.tuweni.bytes.Bytes.of(hash)), diff, code, storage);
    }

    private Fixture persistTransferFixture(final boolean nonceChange, final long transferValue) {
        final var payerId = domainBuilder.entityId();
        final var contractId = domainBuilder.entityId();
        final var accountId = domainBuilder.entityId();
        final var createdTimestamp = domainBuilder.timestamp();
        final var consensusTimestamp = createdTimestamp + 100;
        final var hash = domainBuilder.bytes(32);

        persistBareEntity(accountId, EntityType.ACCOUNT, nonceChange ? 2L : 5L, createdTimestamp);
        persistContractTransactionHash(hash, consensusTimestamp, payerId, contractId);
        domainBuilder
                .contractAction()
                .customize(a -> a.consensusTimestamp(consensusTimestamp)
                        .caller(contractId)
                        .callerType(EntityType.CONTRACT)
                        .recipientAccount(accountId)
                        .value(transferValue)
                        .index(0))
                .persist();

        return new Fixture(hash, createdTimestamp, consensusTimestamp, payerId, contractId, accountId);
    }

    private Fixture persistContractFixture(final byte[] runtimeBytecode) {
        final var payerId = domainBuilder.entityId();
        final var contractId = domainBuilder.entityId();
        final var createdTimestamp = domainBuilder.timestamp();
        final var consensusTimestamp = createdTimestamp + 100;
        final var hash = domainBuilder.bytes(32);

        persistBareEntity(contractId, EntityType.CONTRACT, 3L, createdTimestamp);
        if (runtimeBytecode != null) {
            domainBuilder
                    .contract()
                    .customize(c -> c.id(contractId.getId()).runtimeBytecode(runtimeBytecode))
                    .persist();
        }
        persistContractTransactionHash(hash, consensusTimestamp, payerId, contractId);
        domainBuilder
                .contractAction()
                .customize(a -> a.consensusTimestamp(consensusTimestamp)
                        .caller(payerId)
                        .callerType(EntityType.ACCOUNT)
                        .payerAccountId(payerId)
                        .recipientAccount(null)
                        .recipientContract(contractId)
                        .value(0L))
                .persist();

        return new Fixture(hash, createdTimestamp, consensusTimestamp, payerId, contractId, null);
    }

    private Fixture persistContractFixtureWithAction(final byte[] runtimeBytecode, final long transferValue) {
        final var payerId = domainBuilder.entityId();
        final var contractId = domainBuilder.entityId();
        final var createdTimestamp = domainBuilder.timestamp();
        final var consensusTimestamp = createdTimestamp + 100;
        final var hash = domainBuilder.bytes(32);

        persistBareEntity(contractId, EntityType.CONTRACT, 3L, createdTimestamp);
        if (runtimeBytecode != null) {
            domainBuilder
                    .contract()
                    .customize(c -> c.id(contractId.getId()).runtimeBytecode(runtimeBytecode))
                    .persist();
        }
        persistContractTransactionHash(hash, consensusTimestamp, payerId, contractId);
        domainBuilder
                .contractAction()
                .customize(a -> a.consensusTimestamp(consensusTimestamp)
                        .caller(payerId)
                        .callerType(EntityType.ACCOUNT)
                        .recipientAccount(null)
                        .recipientContract(contractId)
                        .value(transferValue)
                        .index(0))
                .persist();

        return new Fixture(hash, createdTimestamp, consensusTimestamp, payerId, contractId, null);
    }

    private void persistContractTransactionHash(
            final byte[] hash, final long consensusTimestamp, final EntityId payerId, final EntityId contractId) {
        domainBuilder
                .contractTransactionHash()
                .customize(h -> h.hash(hash)
                        .consensusTimestamp(consensusTimestamp)
                        .payerAccountId(payerId.getId())
                        .entityId(contractId.getId()))
                .persist();
    }

    private void persistBareEntity(
            final EntityId entityId, final EntityType type, final long nonce, final long createdTimestamp) {
        domainBuilder
                .entity(entityId, createdTimestamp)
                .customize(e -> e.type(type)
                        .ethereumNonce(nonce)
                        .evmAddress(null)
                        .alias(null)
                        .deleted(false)
                        .timestampRange(Range.atLeast(createdTimestamp)))
                .persist();
    }

    private void persistTreasuryBalance(final long timestamp) {
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(timestamp, systemEntity.treasuryAccount()))
                        .balance(1L))
                .persist();
    }

    private void persistAccountBalance(final EntityId accountId, final long timestamp, final long balance) {
        domainBuilder
                .accountBalance()
                .customize(
                        ab -> ab.id(new AccountBalance.Id(timestamp, accountId)).balance(balance))
                .persist();
    }

    private Fixture persistMultipleAccountsFixture(final int accountCount) {
        final var payerId = domainBuilder.entityId();
        final var contractId = domainBuilder.entityId();
        final var createdTimestamp = domainBuilder.timestamp();
        final var consensusTimestamp = createdTimestamp + 100;
        final var hash = domainBuilder.bytes(32);

        persistTreasuryBalance(createdTimestamp);
        persistContractTransactionHash(hash, consensusTimestamp, payerId, contractId);

        for (int i = 0; i < accountCount; i++) {
            final int index = i;
            final var accountId = domainBuilder.entityId();
            persistBareEntity(accountId, EntityType.CONTRACT, index, createdTimestamp);
            persistAccountBalance(accountId, createdTimestamp, 100L + index);
            domainBuilder
                    .contractAction()
                    .customize(a -> a.consensusTimestamp(consensusTimestamp)
                            .caller(contractId)
                            .callerType(EntityType.CONTRACT)
                            .recipientAccount(null)
                            .recipientContract(accountId)
                            .value(0L)
                            .index(index))
                    .persist();
        }

        return new Fixture(hash, createdTimestamp, consensusTimestamp, payerId, contractId, null);
    }

    private String toLongZeroAddress(final EntityId entityId) {
        return "0x" + bytesToHex(toEvmAddress(entityId));
    }

    private record Fixture(
            byte[] hash,
            long createdTimestamp,
            long consensusTimestamp,
            EntityId payerId,
            EntityId contractId,
            EntityId accountId) {}
}
