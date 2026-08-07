// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.Range;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.hiero.mirror.web3.common.TransactionHashParameter;
import org.hiero.mirror.web3.exception.EntityNotFoundException;
import org.hiero.mirror.web3.service.model.PrestateRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@RequiredArgsConstructor
class PrestateServiceTest extends Web3IntegrationTest {

    private static final byte[] RUNTIME_BYTECODE = new byte[] {0x60, 0x40};
    private static final byte[] STORAGE_SLOT =
            new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};
    private static final byte[] VALUE_READ = new byte[] {0x14};
    private static final byte[] VALUE_WRITTEN = new byte[] {0x28};

    private final PrestateService prestateService;

    @Test
    void callWithDiffEnabledReturnsBothPreAndPost() {
        final var fixture = persistTransferFixture(true);
        persistTreasuryBalance(fixture.createdTimestamp());
        persistAccountBalance(fixture.accountId(), fixture.createdTimestamp(), 100L);
        persistCryptoTransfer(fixture.accountId(), fixture.consensusTimestamp(), 50L);

        final var response = prestateService.processPrestateCall(createRequest(fixture.hash(), true, false, false));

        assertThat(response.getPre()).hasSize(1);
        assertThat(response.getPost()).hasSize(1);
        assertThat(response.getPre().getFirst().getBalance()).isEqualTo("0x64");
        assertThat(response.getPost().getFirst().getBalance()).isEqualTo("0x96");
        assertThat(response.getPost().getFirst().getNonce()).isEqualTo(2L);
    }

    @Test
    void callWithDiffDisabledReturnsOnlyPre() {
        final var fixture = persistTransferFixture(false);
        persistTreasuryBalance(fixture.createdTimestamp());
        persistAccountBalance(fixture.accountId(), fixture.createdTimestamp(), 100L);

        final var response = prestateService.processPrestateCall(createRequest(fixture.hash(), false, false, false));

        assertThat(response.getPre()).hasSize(1);
        assertThat(response.getPost()).isNullOrEmpty();
    }

    @Test
    void callWithDiffEnabledExcludesUnchangedEntries() {
        final var fixture = persistTransferFixture(false);
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
        assertThat(response.getPre().getFirst().getCode())
                .isEqualTo(Bytes.wrap(RUNTIME_BYTECODE).toHex());
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
                .containsEntry(
                        Bytes.wrap(STORAGE_SLOT).toHex(), Bytes.wrap(VALUE_READ).toHex());
    }

    @Test
    void callWithDiffAndStorageEnabledPopulatesPreAndPostStorageFromStateChanges() {
        final var fixture = persistContractFixture(null);
        persistTreasuryBalance(fixture.createdTimestamp());
        persistAccountBalance(fixture.contractId(), fixture.createdTimestamp(), 50L);
        persistCryptoTransfer(fixture.contractId(), fixture.consensusTimestamp(), 10L);
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
                .containsEntry(
                        Bytes.wrap(STORAGE_SLOT).toHex(), Bytes.wrap(VALUE_READ).toHex());
        assertThat(response.getPost().getFirst().getStorage())
                .containsEntry(
                        Bytes.wrap(STORAGE_SLOT).toHex(),
                        Bytes.wrap(VALUE_WRITTEN).toHex());
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
    void callWithContractResultNotFound() {
        final var hash = domainBuilder.bytes(32);
        final var consensusTimestamp = domainBuilder.timestamp();
        domainBuilder
                .contractTransactionHash()
                .customize(h -> h.hash(hash).consensusTimestamp(consensusTimestamp))
                .persist();

        assertThatThrownBy(() -> prestateService.processPrestateCall(createRequest(hash, false, false, false)))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Contract result not found");
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
        persistCryptoTransfer(changedAccount, consensusTimestamp, 50L);

        persistTransactionArtifacts(hash, consensusTimestamp, payerId, contractId);
        domainBuilder
                .contractAction()
                .customize(a -> a.consensusTimestamp(consensusTimestamp)
                        .caller(contractId)
                        .callerType(EntityType.CONTRACT)
                        .recipientAccount(changedAccount)
                        .index(0))
                .persist();
        domainBuilder
                .contractAction()
                .customize(a -> a.consensusTimestamp(consensusTimestamp)
                        .caller(contractId)
                        .callerType(EntityType.CONTRACT)
                        .recipientAccount(unchangedAccount)
                        .index(1))
                .persist();

        final var response = prestateService.processPrestateCall(createRequest(hash, true, false, false));

        assertThat(response.getPre()).hasSize(1);
        assertThat(response.getPost()).hasSize(1);
        assertThat(response.getPre().getFirst().getAddress()).isEqualTo(changedAccount.toString());
        assertThat(response.getPost().getFirst().getBalance()).isEqualTo("0x96");
    }

    @Test
    void callWithoutCodeStillIncludesContractWithBalanceAndNonce() {
        final var fixture = persistContractFixture(null);
        persistTreasuryBalance(fixture.createdTimestamp());
        persistAccountBalance(fixture.contractId(), fixture.createdTimestamp(), 50L);

        final var response = prestateService.processPrestateCall(createRequest(fixture.hash(), false, false, false));

        assertThat(response.getPre()).hasSize(1);
        assertThat(response.getPre().getFirst().getAddress())
                .isEqualTo(fixture.contractId().toString());
        assertThat(response.getPre().getFirst().getBalance()).isEqualTo("0x32");
        assertThat(response.getPre().getFirst().getNonce()).isEqualTo(3L);
        assertThat(response.getPre().getFirst().getCode()).isNull();
        assertThat(response.getPre().getFirst().getStorage()).isNullOrEmpty();
    }

    @Test
    void callWithCodeLoadsBytecodeByIdsAndTimestamp() {
        final var fixture = persistContractFixture(RUNTIME_BYTECODE);

        final var response = prestateService.processPrestateCall(createRequest(fixture.hash(), false, true, false));

        assertThat(response.getPre()).hasSize(1);
        assertThat(response.getPre().getFirst().getAddress())
                .isEqualTo(fixture.contractId().toString());
        assertThat(response.getPre().getFirst().getCode())
                .isEqualTo(Bytes.wrap(RUNTIME_BYTECODE).toHex());
    }

    @Test
    void callWithDiffAndCodePopulatesPreAndPostBytecode() {
        final var fixture = persistContractFixture(RUNTIME_BYTECODE);
        persistTreasuryBalance(fixture.createdTimestamp());
        persistAccountBalance(fixture.contractId(), fixture.createdTimestamp(), 50L);
        persistCryptoTransfer(fixture.contractId(), fixture.consensusTimestamp(), 10L);

        final var response = prestateService.processPrestateCall(createRequest(fixture.hash(), true, true, false));

        assertThat(response.getPre()).hasSize(1);
        assertThat(response.getPre().getFirst().getCode())
                .isEqualTo(Bytes.wrap(RUNTIME_BYTECODE).toHex());
        assertThat(response.getPost()).hasSize(1);
        assertThat(response.getPost().getFirst().getCode())
                .isEqualTo(Bytes.wrap(RUNTIME_BYTECODE).toHex());
    }

    @Test
    void callIncludesMirrorRecipientAddress() {
        final var payerId = domainBuilder.entityId();
        final var contractId = domainBuilder.entityId();
        final var mirrorAccount = domainBuilder.entityId();
        final var createdTimestamp = domainBuilder.timestamp();
        final var consensusTimestamp = createdTimestamp + 100;
        final var hash = domainBuilder.bytes(32);

        persistBareEntity(mirrorAccount, EntityType.ACCOUNT, 1L, createdTimestamp);
        persistTransactionArtifacts(hash, consensusTimestamp, payerId, contractId);
        domainBuilder
                .contractAction()
                .customize(a -> a.consensusTimestamp(consensusTimestamp)
                        .caller(contractId)
                        .callerType(EntityType.CONTRACT)
                        .recipientAccount(null)
                        .recipientContract(null)
                        .recipientAddress(DomainUtils.toEvmAddress(mirrorAccount))
                        .index(0))
                .persist();

        final var response = prestateService.processPrestateCall(createRequest(hash, false, false, false));

        assertThat(response.getPre()).hasSize(1);
        assertThat(response.getPre().getFirst().getAddress()).isEqualTo(mirrorAccount.toString());
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
        final var fixture = persistTransferFixture(false);
        persistTreasuryBalance(fixture.createdTimestamp());
        persistAccountBalance(fixture.accountId(), fixture.createdTimestamp(), 100L);
        if (diff) {
            persistCryptoTransfer(fixture.accountId(), fixture.consensusTimestamp(), 1L);
        }

        final var response = prestateService.processPrestateCall(createRequest(fixture.hash(), diff, code, storage));

        assertThat(response.getPre()).isNotNull();
        if (diff) {
            assertThat(response.getPost()).isNotNull();
        } else {
            assertThat(response.getPost()).isNullOrEmpty();
        }
    }

    private PrestateRequest createRequest(
            final byte[] hash, final boolean diff, final boolean code, final boolean storage) {
        return new PrestateRequest(
                new TransactionHashParameter(org.apache.tuweni.bytes.Bytes.of(hash)), diff, code, storage);
    }

    private Fixture persistTransferFixture(final boolean nonceChange) {
        final var payerId = domainBuilder.entityId();
        final var contractId = domainBuilder.entityId();
        final var accountId = domainBuilder.entityId();
        final var createdTimestamp = domainBuilder.timestamp();
        final var consensusTimestamp = createdTimestamp + 100;
        final var hash = domainBuilder.bytes(32);

        persistBareEntity(accountId, EntityType.ACCOUNT, nonceChange ? 2L : 5L, createdTimestamp);
        persistTransactionArtifacts(hash, consensusTimestamp, payerId, contractId);
        domainBuilder
                .contractAction()
                .customize(a -> a.consensusTimestamp(consensusTimestamp)
                        .caller(contractId)
                        .callerType(EntityType.CONTRACT)
                        .recipientAccount(accountId)
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
        persistTransactionArtifacts(hash, consensusTimestamp, payerId, contractId);

        return new Fixture(hash, createdTimestamp, consensusTimestamp, payerId, contractId, null);
    }

    private void persistTransactionArtifacts(
            final byte[] hash, final long consensusTimestamp, final EntityId payerId, final EntityId contractId) {
        domainBuilder
                .contractTransactionHash()
                .customize(h -> h.hash(hash)
                        .consensusTimestamp(consensusTimestamp)
                        .payerAccountId(payerId.getId())
                        .entityId(contractId.getId()))
                .persist();
        domainBuilder
                .contractResult()
                .customize(r -> r.consensusTimestamp(consensusTimestamp)
                        .senderId(payerId)
                        .contractId(contractId.getId()))
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

    private void persistCryptoTransfer(final EntityId accountId, final long consensusTimestamp, final long amount) {
        domainBuilder
                .cryptoTransfer()
                .customize(t -> t.entityId(accountId.getId())
                        .consensusTimestamp(consensusTimestamp)
                        .amount(amount))
                .persist();
    }

    private record Fixture(
            byte[] hash,
            long createdTimestamp,
            long consensusTimestamp,
            EntityId payerId,
            EntityId contractId,
            EntityId accountId) {}
}
