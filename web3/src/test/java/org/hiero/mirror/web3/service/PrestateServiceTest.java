// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.common.domain.transaction.TransactionType.CRYPTOCREATEACCOUNT;
import static org.hiero.mirror.common.util.DomainUtils.bytesToHex;
import static org.hiero.mirror.common.util.DomainUtils.toEvmAddress;
import static org.hiero.mirror.common.util.SignatureUtils.EC_DOMAIN_PARAMETERS;
import static org.hiero.mirror.web3.utils.ByteUtils.wrapToWordSize;

import com.google.common.collect.Range;
import com.google.protobuf.Int64Value;
import com.hedera.node.app.hapi.utils.ethereum.CodeDelegation;
import com.hedera.services.stream.proto.CallOperationType;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractNonceInfo;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import jakarta.annotation.Resource;
import java.math.BigInteger;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.transaction.Authorization;
import org.hiero.mirror.common.util.SignatureUtils;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.hiero.mirror.web3.common.TransactionHashParameter;
import org.hiero.mirror.web3.controller.PrestateProperties;
import org.hiero.mirror.web3.exception.EntityNotFoundException;
import org.hiero.mirror.web3.service.model.PrestateRequest;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECP256K1;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@RequiredArgsConstructor
final class PrestateServiceTest extends Web3IntegrationTest {

    private static final SECP256K1 SECP256K1 = new SECP256K1();
    private static final byte[] RUNTIME_BYTECODE = new byte[] {0x60, 0x40};
    private static final byte[] STORAGE_SLOT =
            new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};
    private static final byte[] VALUE_READ = new byte[] {0x14};
    private static final byte[] VALUE_WRITTEN = new byte[] {0x28};
    private static final int DEFAULT_MAX_TOUCHED_ACCOUNTS = 1000;

    private final PrestateService prestateService;

    @Resource
    private PrestateProperties prestateProperties;

    @AfterEach
    void tearDown() {
        prestateProperties.setMaxTouchedAccounts(DEFAULT_MAX_TOUCHED_ACCOUNTS);
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
        assertThat(response.getPost().getFirst().getNonce()).isEqualTo(0L);
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
        assertThat(response.getPre().getFirst().getCode()).isEqualTo(rawHex(RUNTIME_BYTECODE));
    }

    @Test
    void callWithCodeEnabledOmitsCodeForEmptyBytecode() {
        // Empty runtime bytecode should NOT be emitted as a padded word of zeros (Geth returns raw bytecode).
        final var fixture = persistContractFixture(new byte[0]);

        final var response = prestateService.processPrestateCall(createRequest(fixture.hash(), false, true, false));

        assertThat(response.getPre()).hasSize(1);
        assertThat(response.getPre().getFirst().getCode()).isNull();
    }

    @Test
    void callWithCodeEnabledReturnsFullBytecodeForLongContract() {
        final var longBytecode = new byte[128];
        for (int i = 0; i < longBytecode.length; i++) {
            longBytecode[i] = (byte) (i + 1);
        }
        final var fixture = persistContractFixture(longBytecode);

        final var response = prestateService.processPrestateCall(createRequest(fixture.hash(), false, true, false));

        assertThat(response.getPre()).hasSize(1);
        assertThat(response.getPre().getFirst().getCode()).isEqualTo(rawHex(longBytecode));
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
    void callWithDiffAndStorageDetectsClearedSlotWhenValueWrittenIsNull() {
        final var fixture = persistContractFixture(null);
        persistTreasuryBalance(fixture.createdTimestamp());
        persistAccountBalance(fixture.contractId(), fixture.createdTimestamp(), 50L);
        // Slot was cleared during the transaction (value_read is set, value_written is null)
        domainBuilder
                .contractStateChange()
                .customize(c -> c.consensusTimestamp(fixture.consensusTimestamp())
                        .contractId(fixture.contractId().getId())
                        .slot(STORAGE_SLOT)
                        .valueRead(VALUE_READ)
                        .valueWritten(null))
                .persist();

        final var response = prestateService.processPrestateCall(createRequest(fixture.hash(), true, false, true));

        assertThat(response.getPre()).hasSize(1);
        assertThat(response.getPost()).hasSize(1);
        assertThat(response.getPre().getFirst().getStorage())
                .containsEntry(wrapToWordSize(STORAGE_SLOT), wrapToWordSize(VALUE_READ));
        // Post storage should not contain the cleared slot
        assertThat(response.getPost().getFirst().getStorage()).isNullOrEmpty();
    }

    @Test
    void callWithDiffAndStorageOmitsEmptyPreSlotForNewValue() {
        final var fixture = persistContractFixture(null);
        persistTreasuryBalance(fixture.createdTimestamp());
        persistAccountBalance(fixture.contractId(), fixture.createdTimestamp(), 50L);
        domainBuilder
                .contractStateChange()
                .customize(c -> c.consensusTimestamp(fixture.consensusTimestamp())
                        .contractId(fixture.contractId().getId())
                        .slot(STORAGE_SLOT)
                        .valueRead(new byte[0])
                        .valueWritten(VALUE_WRITTEN))
                .persist();

        final var response = prestateService.processPrestateCall(createRequest(fixture.hash(), true, false, true));

        assertThat(response.getPre()).hasSize(1);
        assertThat(response.getPost()).hasSize(1);
        assertThat(response.getPre().getFirst().getStorage()).isNullOrEmpty();
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
    void callWithDiffOmitsTokenCallTargets() {
        final var payerId = domainBuilder.entityId();
        final var contractId = domainBuilder.entityId();
        final var tokenId = domainBuilder.entityId();
        final var changedAccount = domainBuilder.entityId();
        final var createdTimestamp = domainBuilder.timestamp();
        final var consensusTimestamp = createdTimestamp + 100;
        final var hash = domainBuilder.bytes(32);

        persistBareEntity(tokenId, EntityType.TOKEN, 0L, createdTimestamp);
        persistBareEntity(changedAccount, EntityType.ACCOUNT, 1L, createdTimestamp);
        persistTreasuryBalance(createdTimestamp);
        persistAccountBalance(changedAccount, createdTimestamp, 100L);
        persistContractTransactionHash(hash, consensusTimestamp, payerId, contractId);
        domainBuilder
                .contractAction()
                .customize(a -> a.consensusTimestamp(consensusTimestamp)
                        .caller(contractId)
                        .callerType(EntityType.CONTRACT)
                        .recipientAccount(null)
                        .recipientContract(tokenId)
                        .value(25L)
                        .index(0))
                .persist();
        domainBuilder
                .contractAction()
                .customize(a -> a.consensusTimestamp(consensusTimestamp)
                        .caller(contractId)
                        .callerType(EntityType.CONTRACT)
                        .recipientAccount(changedAccount)
                        .value(50L)
                        .index(1))
                .persist();

        final var response = prestateService.processPrestateCall(createRequest(hash, true, false, false));

        final var tokenAddress = toLongZeroAddress(tokenId);
        assertThat(response.getPre()).extracting(t -> t.getAddress()).doesNotContain(tokenAddress);
        assertThat(response.getPost()).extracting(t -> t.getAddress()).doesNotContain(tokenAddress);
        assertThat(response.getPre())
                .extracting(t -> t.getAddress())
                .containsExactly(toLongZeroAddress(changedAccount));
    }

    @Test
    void callWithDiffEnabledDetectsOnlyNonceChange() {
        final var senderId = domainBuilder.entityId();
        final var contractId = domainBuilder.entityId();
        final var createdTimestamp = domainBuilder.timestamp();
        final var consensusTimestamp = createdTimestamp + 100;
        final var hash = domainBuilder.bytes(32);

        persistBareEntity(senderId, EntityType.ACCOUNT, 4L, createdTimestamp);
        persistTreasuryBalance(createdTimestamp);
        persistAccountBalance(senderId, createdTimestamp, 100L);
        persistEthereumCall(
                hash,
                consensusTimestamp,
                senderId,
                senderId,
                contractId,
                3L,
                signerNonceFunctionResult(senderId, 4L),
                List.of());
        persistCallAction(
                consensusTimestamp, senderId, EntityType.ACCOUNT, contractId, 0L, CallOperationType.OP_CALL, 0);

        final var response = prestateService.processPrestateCall(createRequest(hash, true, false, false));

        assertThat(response.getPre()).hasSize(1);
        assertThat(response.getPost()).hasSize(1);
        assertThat(response.getPre().getFirst().getNonce()).isEqualTo(3L);
        assertThat(response.getPost().getFirst().getNonce()).isEqualTo(4L);
    }

    @Test
    void callWithDiffEnabledDetectsEthereumSenderNonceWithoutSignerNonce() {
        final var senderId = domainBuilder.entityId();
        final var contractId = domainBuilder.entityId();
        final var createdTimestamp = domainBuilder.timestamp();
        final var consensusTimestamp = createdTimestamp + 100;
        final var hash = domainBuilder.bytes(32);

        persistBareEntity(senderId, EntityType.ACCOUNT, 10L, createdTimestamp);
        persistTreasuryBalance(createdTimestamp);
        persistAccountBalance(senderId, createdTimestamp, 100L);
        persistEthereumCall(hash, consensusTimestamp, senderId, senderId, contractId, 9L, new byte[0], List.of());
        persistCallAction(
                consensusTimestamp, senderId, EntityType.ACCOUNT, contractId, 0L, CallOperationType.OP_CALL, 0);

        final var response = prestateService.processPrestateCall(createRequest(hash, true, false, false));

        assertThat(response.getPre())
                .singleElement()
                .extracting(t -> t.getNonce())
                .isEqualTo(9L);
        assertThat(response.getPost())
                .singleElement()
                .extracting(t -> t.getNonce())
                .isEqualTo(10L);
    }

    @Test
    void callWithoutDiffUsesReconstructedPreNonceForEthereumSender() {
        final var senderId = domainBuilder.entityId();
        final var contractId = domainBuilder.entityId();
        final var createdTimestamp = domainBuilder.timestamp();
        final var consensusTimestamp = createdTimestamp + 100;
        final var hash = domainBuilder.bytes(32);

        persistBareEntity(senderId, EntityType.ACCOUNT, 4L, createdTimestamp);
        persistTreasuryBalance(createdTimestamp);
        persistAccountBalance(senderId, createdTimestamp, 100L);
        persistEthereumCall(
                hash,
                consensusTimestamp,
                senderId,
                senderId,
                contractId,
                3L,
                signerNonceFunctionResult(senderId, 4L),
                List.of());
        persistCallAction(
                consensusTimestamp, senderId, EntityType.ACCOUNT, contractId, 0L, CallOperationType.OP_CALL, 0);

        final var response = prestateService.processPrestateCall(createRequest(hash, false, false, false));

        assertThat(response.getPre())
                .singleElement()
                .extracting(t -> t.getNonce())
                .isEqualTo(3L);
        assertThat(response.getPost()).isNullOrEmpty();
    }

    @Test
    void callWithDiffEnabledOmitsHapiSenderWithoutNonceChange() {
        final var senderId = domainBuilder.entityId();
        final var contractId = domainBuilder.entityId();
        final var createdTimestamp = domainBuilder.timestamp();
        final var consensusTimestamp = createdTimestamp + 100;
        final var hash = domainBuilder.bytes(32);

        persistBareEntity(senderId, EntityType.ACCOUNT, 10L, createdTimestamp);
        persistTreasuryBalance(createdTimestamp);
        persistAccountBalance(senderId, createdTimestamp, 100L);
        persistContractTransactionHash(hash, consensusTimestamp, senderId, contractId);
        domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(consensusTimestamp)
                        .payerAccountId(senderId)
                        .senderId(senderId)
                        .contractId(contractId.getId())
                        .createdContractIds(List.of())
                        .functionResult(new byte[0])
                        .amount(0L))
                .persist();
        persistCallAction(
                consensusTimestamp, senderId, EntityType.ACCOUNT, contractId, 0L, CallOperationType.OP_CALL, 0);

        final var response = prestateService.processPrestateCall(createRequest(hash, true, false, false));

        assertThat(response.getPre()).isEmpty();
        assertThat(response.getPost()).isEmpty();
    }

    @Test
    void callWithDiffEnabledDoesNotInferInnerCreateNonceFromEntity() {
        final var callerId = domainBuilder.entityId();
        final var createdContractId = domainBuilder.entityId();
        final var payerId = domainBuilder.entityId();
        final var createdTimestamp = domainBuilder.timestamp();
        final var consensusTimestamp = createdTimestamp + 100;
        final var hash = domainBuilder.bytes(32);

        persistBareEntity(callerId, EntityType.CONTRACT, 8L, createdTimestamp);
        persistTreasuryBalance(createdTimestamp);
        persistAccountBalance(callerId, createdTimestamp, 100L);
        persistContractTransactionHash(hash, consensusTimestamp, payerId, callerId);
        persistCallAction(
                consensusTimestamp,
                callerId,
                EntityType.CONTRACT,
                createdContractId,
                0L,
                CallOperationType.OP_CREATE,
                1);

        final var response = prestateService.processPrestateCall(createRequest(hash, true, false, false));

        assertThat(response.getPre()).isEmpty();
        assertThat(response.getPost()).isEmpty();
    }

    @Test
    void callWithDiffEnabledUsesCreatedContractNonceFromFunctionResult() {
        final var callerId = domainBuilder.entityId();
        final var createdContractId = domainBuilder.entityId();
        final var payerId = domainBuilder.entityId();
        final var createdTimestamp = domainBuilder.timestamp();
        final var consensusTimestamp = createdTimestamp + 100;
        final var hash = domainBuilder.bytes(32);

        persistBareEntity(callerId, EntityType.CONTRACT, 2L, createdTimestamp);
        persistBareEntity(createdContractId, EntityType.CONTRACT, 1L, consensusTimestamp);
        persistTreasuryBalance(createdTimestamp);
        persistAccountBalance(callerId, createdTimestamp, 100L);
        persistContractTransactionHash(hash, consensusTimestamp, payerId, callerId);
        domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(consensusTimestamp)
                        .payerAccountId(payerId)
                        .senderId(payerId)
                        .contractId(callerId.getId())
                        .createdContractIds(List.of(createdContractId.getId()))
                        .functionResult(createdContractNonceFunctionResult(createdContractId, 1L))
                        .amount(0L))
                .persist();
        persistCallAction(
                consensusTimestamp,
                callerId,
                EntityType.CONTRACT,
                createdContractId,
                0L,
                CallOperationType.OP_CREATE,
                1);

        final var response = prestateService.processPrestateCall(createRequest(hash, true, false, false));

        final var createdAddress = toLongZeroAddress(createdContractId);
        assertThat(response.getPre()).extracting(t -> t.getAddress()).doesNotContain(createdAddress);
        assertThat(response.getPost())
                .filteredOn(t -> createdAddress.equals(t.getAddress()))
                .singleElement()
                .extracting(t -> t.getNonce())
                .isEqualTo(1L);
    }

    @Test
    void callWithDiffEnabledDetectsEip7702AuthorityNonce() {
        final var senderId = domainBuilder.entityId();
        final var authorityId = domainBuilder.entityId();
        final var contractId = domainBuilder.entityId();
        final var createdTimestamp = domainBuilder.timestamp();
        final var consensusTimestamp = createdTimestamp + 100;
        final var hash = domainBuilder.bytes(32);
        final var keyPair = SECP256K1.generateKeyPair();
        final var authorityAddress = evmAddressFromKeyPair(keyPair);
        final var target = domainBuilder.bytes(20);
        final var authorization = signedAuthorization(keyPair, target, 4L);

        persistBareEntity(senderId, EntityType.ACCOUNT, 10L, createdTimestamp);
        persistBareEntity(authorityId, EntityType.ACCOUNT, 99L, createdTimestamp, authorityAddress);
        persistTreasuryBalance(createdTimestamp);
        persistAccountBalance(senderId, createdTimestamp, 100L);
        persistAccountBalance(authorityId, createdTimestamp, 50L);
        persistEthereumCall(
                hash, consensusTimestamp, senderId, senderId, contractId, 9L, new byte[0], List.of(authorization));
        persistCallAction(
                consensusTimestamp, senderId, EntityType.ACCOUNT, contractId, 0L, CallOperationType.OP_CALL, 0);

        final var response = prestateService.processPrestateCall(createRequest(hash, true, false, false));

        final var authorityHex = "0x" + bytesToHex(authorityAddress);
        final var authorityPre = response.getPre().stream()
                .filter(t -> authorityHex.equals(t.getAddress()))
                .findFirst()
                .orElseThrow();
        final var authorityPost = response.getPost().stream()
                .filter(t -> authorityHex.equals(t.getAddress()))
                .findFirst()
                .orElseThrow();
        assertThat(authorityPre.getNonce()).isEqualTo(4L);
        assertThat(authorityPost.getNonce()).isEqualTo(5L);
    }

    @Test
    void callWithDiffEnabledDetectsEip7702AuthorityNonceResolvedByAlias() {
        final var senderId = domainBuilder.entityId();
        final var authorityId = domainBuilder.entityId();
        final var contractId = domainBuilder.entityId();
        final var createdTimestamp = domainBuilder.timestamp();
        final var consensusTimestamp = createdTimestamp + 100;
        final var hash = domainBuilder.bytes(32);
        final var keyPair = SECP256K1.generateKeyPair();
        final var authorityAddress = evmAddressFromKeyPair(keyPair);
        final var authorization = signedAuthorization(keyPair, domainBuilder.bytes(20), 4L);

        persistBareEntity(senderId, EntityType.ACCOUNT, 10L, createdTimestamp);
        persistBareEntity(authorityId, EntityType.ACCOUNT, 99L, createdTimestamp, null, authorityAddress);
        persistTreasuryBalance(createdTimestamp);
        persistAccountBalance(senderId, createdTimestamp, 100L);
        persistAccountBalance(authorityId, createdTimestamp, 50L);
        persistEthereumCall(
                hash, consensusTimestamp, senderId, senderId, contractId, 9L, new byte[0], List.of(authorization));
        persistCallAction(
                consensusTimestamp, senderId, EntityType.ACCOUNT, contractId, 0L, CallOperationType.OP_CALL, 0);

        final var response = prestateService.processPrestateCall(createRequest(hash, true, false, false));

        final var authorityHex = "0x" + bytesToHex(authorityAddress);
        final var authorityPre = response.getPre().stream()
                .filter(t -> authorityHex.equals(t.getAddress()))
                .findFirst()
                .orElseThrow();
        final var authorityPost = response.getPost().stream()
                .filter(t -> authorityHex.equals(t.getAddress()))
                .findFirst()
                .orElseThrow();
        assertThat(authorityPre.getNonce()).isEqualTo(4L);
        assertThat(authorityPost.getNonce()).isEqualTo(5L);
    }

    @Test
    void callWithDiffEnabledDetectsSelfSponsoredEip7702Nonce() {
        final var senderId = domainBuilder.entityId();
        final var contractId = domainBuilder.entityId();
        final var createdTimestamp = domainBuilder.timestamp();
        final var consensusTimestamp = createdTimestamp + 100;
        final var hash = domainBuilder.bytes(32);
        final var keyPair = SECP256K1.generateKeyPair();
        final var senderAddress = evmAddressFromKeyPair(keyPair);
        final var authorization = signedAuthorization(keyPair, domainBuilder.bytes(20), 11L);

        persistBareEntity(senderId, EntityType.ACCOUNT, 999L, createdTimestamp, senderAddress);
        persistTreasuryBalance(createdTimestamp);
        persistAccountBalance(senderId, createdTimestamp, 100L);
        persistEthereumCall(
                hash,
                consensusTimestamp,
                senderId,
                senderId,
                contractId,
                10L,
                signerNonceFunctionResult(senderId, 12L),
                List.of(authorization));
        persistCallAction(
                consensusTimestamp, senderId, EntityType.ACCOUNT, contractId, 0L, CallOperationType.OP_CALL, 0);

        final var response = prestateService.processPrestateCall(createRequest(hash, true, false, false));

        assertThat(response.getPre())
                .singleElement()
                .extracting(t -> t.getNonce())
                .isEqualTo(10L);
        assertThat(response.getPost())
                .singleElement()
                .extracting(t -> t.getNonce())
                .isEqualTo(12L);
    }

    @Test
    void callWithDiffEnabledDetectsMultipleEip7702AuthorityNonces() {
        final var senderId = domainBuilder.entityId();
        final var firstAuthorityId = domainBuilder.entityId();
        final var secondAuthorityId = domainBuilder.entityId();
        final var contractId = domainBuilder.entityId();
        final var createdTimestamp = domainBuilder.timestamp();
        final var consensusTimestamp = createdTimestamp + 100;
        final var hash = domainBuilder.bytes(32);
        final var firstKeyPair = SECP256K1.generateKeyPair();
        final var secondKeyPair = SECP256K1.generateKeyPair();
        final var firstAuthorityAddress = evmAddressFromKeyPair(firstKeyPair);
        final var secondAuthorityAddress = evmAddressFromKeyPair(secondKeyPair);
        final var firstAuthorization = signedAuthorization(firstKeyPair, domainBuilder.bytes(20), 4L);
        final var secondAuthorization = signedAuthorization(secondKeyPair, domainBuilder.bytes(20), 7L);

        persistBareEntity(senderId, EntityType.ACCOUNT, 10L, createdTimestamp);
        persistBareEntity(firstAuthorityId, EntityType.ACCOUNT, 99L, createdTimestamp, firstAuthorityAddress);
        persistBareEntity(secondAuthorityId, EntityType.ACCOUNT, 99L, createdTimestamp, secondAuthorityAddress);
        persistTreasuryBalance(createdTimestamp);
        persistAccountBalance(senderId, createdTimestamp, 100L);
        persistAccountBalance(firstAuthorityId, createdTimestamp, 50L);
        persistAccountBalance(secondAuthorityId, createdTimestamp, 25L);
        persistEthereumCall(
                hash,
                consensusTimestamp,
                senderId,
                senderId,
                contractId,
                9L,
                new byte[0],
                List.of(firstAuthorization, secondAuthorization));
        persistCallAction(
                consensusTimestamp, senderId, EntityType.ACCOUNT, contractId, 0L, CallOperationType.OP_CALL, 0);

        final var response = prestateService.processPrestateCall(createRequest(hash, true, false, false));

        final var firstAuthorityHex = "0x" + bytesToHex(firstAuthorityAddress);
        final var secondAuthorityHex = "0x" + bytesToHex(secondAuthorityAddress);
        final var firstAuthorityPre = response.getPre().stream()
                .filter(t -> firstAuthorityHex.equals(t.getAddress()))
                .findFirst()
                .orElseThrow();
        final var firstAuthorityPost = response.getPost().stream()
                .filter(t -> firstAuthorityHex.equals(t.getAddress()))
                .findFirst()
                .orElseThrow();
        final var secondAuthorityPre = response.getPre().stream()
                .filter(t -> secondAuthorityHex.equals(t.getAddress()))
                .findFirst()
                .orElseThrow();
        final var secondAuthorityPost = response.getPost().stream()
                .filter(t -> secondAuthorityHex.equals(t.getAddress()))
                .findFirst()
                .orElseThrow();
        assertThat(firstAuthorityPre.getNonce()).isEqualTo(4L);
        assertThat(firstAuthorityPost.getNonce()).isEqualTo(5L);
        assertThat(secondAuthorityPre.getNonce()).isEqualTo(7L);
        assertThat(secondAuthorityPost.getNonce()).isEqualTo(8L);
    }

    @Test
    void callWithDiffEnabledSkipsUnresolvedEip7702Authorities() {
        final var senderId = domainBuilder.entityId();
        final var authorityId = domainBuilder.entityId();
        final var contractId = domainBuilder.entityId();
        final var createdTimestamp = domainBuilder.timestamp();
        final var consensusTimestamp = createdTimestamp + 100;
        final var hash = domainBuilder.bytes(32);
        final var knownKeyPair = SECP256K1.generateKeyPair();
        final var unknownKeyPair = SECP256K1.generateKeyPair();
        final var authorityAddress = evmAddressFromKeyPair(knownKeyPair);
        final var knownAuthorization = signedAuthorization(knownKeyPair, domainBuilder.bytes(20), 4L);
        final var unknownAuthorization = signedAuthorization(unknownKeyPair, domainBuilder.bytes(20), 1L);
        final var invalidAuthorization = Authorization.builder()
                .address("0x" + bytesToHex(domainBuilder.bytes(20)))
                .chainId("0x0")
                .build();

        persistBareEntity(senderId, EntityType.ACCOUNT, 10L, createdTimestamp);
        persistBareEntity(authorityId, EntityType.ACCOUNT, 99L, createdTimestamp, authorityAddress);
        persistTreasuryBalance(createdTimestamp);
        persistAccountBalance(senderId, createdTimestamp, 100L);
        persistAccountBalance(authorityId, createdTimestamp, 50L);
        persistEthereumCall(
                hash,
                consensusTimestamp,
                senderId,
                senderId,
                contractId,
                9L,
                new byte[0],
                List.of(invalidAuthorization, unknownAuthorization, knownAuthorization));
        persistCallAction(
                consensusTimestamp, senderId, EntityType.ACCOUNT, contractId, 0L, CallOperationType.OP_CALL, 0);

        final var response = prestateService.processPrestateCall(createRequest(hash, true, false, false));

        final var authorityHex = "0x" + bytesToHex(authorityAddress);
        final var unknownAuthorityHex = "0x" + bytesToHex(evmAddressFromKeyPair(unknownKeyPair));
        assertThat(response.getPre())
                .extracting(t -> t.getAddress())
                .contains(authorityHex)
                .doesNotContain(unknownAuthorityHex);
        final var authorityPre = response.getPre().stream()
                .filter(t -> authorityHex.equals(t.getAddress()))
                .findFirst()
                .orElseThrow();
        final var authorityPost = response.getPost().stream()
                .filter(t -> authorityHex.equals(t.getAddress()))
                .findFirst()
                .orElseThrow();
        assertThat(authorityPre.getNonce()).isEqualTo(4L);
        assertThat(authorityPost.getNonce()).isEqualTo(5L);
    }

    @Test
    void callResolvesLatestEntityWhenBothCurrentAndHistoryExist() {
        final var payerId = domainBuilder.entityId();
        final var contractId = domainBuilder.entityId();
        final var accountId = domainBuilder.entityId();
        final var createdTimestamp = domainBuilder.timestamp();
        final var midTimestamp = createdTimestamp + 50;
        final var consensusTimestamp = createdTimestamp + 100;
        final var hash = domainBuilder.bytes(32);

        // Multiple historical versions plus a current one; the newest version at query time has nonce 42
        domainBuilder
                .entityHistory(accountId, createdTimestamp)
                .customize(e -> e.type(EntityType.ACCOUNT)
                        .ethereumNonce(1L)
                        .evmAddress(null)
                        .alias(null)
                        .deleted(false)
                        .timestampRange(Range.closedOpen(createdTimestamp, midTimestamp)))
                .persist();
        domainBuilder
                .entityHistory(accountId, createdTimestamp)
                .customize(e -> e.type(EntityType.ACCOUNT)
                        .ethereumNonce(7L)
                        .evmAddress(null)
                        .alias(null)
                        .deleted(false)
                        .timestampRange(Range.closedOpen(midTimestamp, consensusTimestamp - 1)))
                .persist();
        domainBuilder
                .entity(accountId, createdTimestamp)
                .customize(e -> e.type(EntityType.ACCOUNT)
                        .ethereumNonce(42L)
                        .evmAddress(null)
                        .alias(null)
                        .deleted(false)
                        .timestampRange(Range.atLeast(consensusTimestamp - 1)))
                .persist();

        persistTreasuryBalance(createdTimestamp);
        persistAccountBalance(accountId, createdTimestamp, 100L);

        persistContractTransactionHash(hash, consensusTimestamp, payerId, contractId);
        domainBuilder
                .contractAction()
                .customize(a -> a.consensusTimestamp(consensusTimestamp)
                        .caller(accountId)
                        .callerType(EntityType.ACCOUNT)
                        .recipientContract(contractId)
                        .value(0L)
                        .index(0))
                .persist();

        final var response = prestateService.processPrestateCall(createRequest(hash, false, false, false));

        assertThat(response.getPre()).hasSize(1);
        assertThat(response.getPre().getFirst().getNonce()).isEqualTo(0L);
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
        assertThat(response.getPre().getFirst().getNonce()).isEqualTo(0L);
        assertThat(response.getPre().getFirst().getCode()).isNull();
        assertThat(response.getPre().getFirst().getStorage()).isNullOrEmpty();
    }

    @Test
    void callWithCodeLoadsBytecodeByIdsAndTimestamp() {
        final var fixture = persistContractFixture(RUNTIME_BYTECODE);

        final var response = prestateService.processPrestateCall(createRequest(fixture.hash(), false, true, false));

        assertThat(response.getPre()).hasSize(1);
        assertThat(response.getPre().getFirst().getAddress()).isEqualTo(toLongZeroAddress(fixture.contractId()));
        assertThat(response.getPre().getFirst().getCode()).isEqualTo(rawHex(RUNTIME_BYTECODE));
    }

    @Test
    void callWithDiffAndCodePopulatesPreAndPostBytecode() {
        final var fixture = persistContractFixtureWithAction(RUNTIME_BYTECODE, 10L);
        persistTreasuryBalance(fixture.createdTimestamp());
        persistAccountBalance(fixture.contractId(), fixture.createdTimestamp(), 50L);

        final var response = prestateService.processPrestateCall(createRequest(fixture.hash(), true, true, false));

        assertThat(response.getPre()).hasSize(1);
        assertThat(response.getPre().getFirst().getCode()).isEqualTo(rawHex(RUNTIME_BYTECODE));
        assertThat(response.getPost()).hasSize(1);
        assertThat(response.getPost().getFirst().getCode()).isEqualTo(rawHex(RUNTIME_BYTECODE));
    }

    @Test
    void callWithDiffTreatsPrecedingHollowCreateAsBornInThisTransaction() {
        final var payerId = domainBuilder.entityId();
        final var contractId = domainBuilder.entityId();
        final var hollowAccount = domainBuilder.entityId();
        final var createdTimestamp = domainBuilder.timestamp();
        final var consensusTimestamp = createdTimestamp + 100;
        final var hollowCreateTimestamp = consensusTimestamp - 1L;
        final var hash = domainBuilder.bytes(32);

        persistBareEntity(hollowAccount, EntityType.ACCOUNT, 7L, hollowCreateTimestamp);
        persistTreasuryBalance(createdTimestamp);
        persistSuccessfulCryptoCreateChild(hollowAccount, consensusTimestamp, hollowCreateTimestamp);
        persistContractTransactionHash(hash, consensusTimestamp, payerId, contractId);
        domainBuilder
                .contractAction()
                .customize(a -> a.consensusTimestamp(consensusTimestamp)
                        .caller(contractId)
                        .callerType(EntityType.CONTRACT)
                        .recipientAccount(hollowAccount)
                        .value(25L)
                        .index(0))
                .persist();

        final var response = prestateService.processPrestateCall(createRequest(hash, true, false, false));

        final var hollowAddress = toLongZeroAddress(hollowAccount);
        assertThat(response.getPre()).extracting(t -> t.getAddress()).doesNotContain(hollowAddress);
        assertThat(response.getPost())
                .filteredOn(t -> hollowAddress.equals(t.getAddress()))
                .singleElement()
                .satisfies(t -> {
                    assertThat(t.getNonce()).isEqualTo(0L);
                    assertThat(t.getBalance()).isEqualTo("0x3a35294400");
                });
    }

    @Test
    void callWithDiffSkipsFailedCryptoCreateChildren() {
        final var payerId = domainBuilder.entityId();
        final var contractId = domainBuilder.entityId();
        final var failedAccount = domainBuilder.entityId();
        final var createdTimestamp = domainBuilder.timestamp();
        final var consensusTimestamp = createdTimestamp + 100;
        final var hash = domainBuilder.bytes(32);

        persistBareEntity(failedAccount, EntityType.ACCOUNT, 0L, consensusTimestamp);
        persistTreasuryBalance(createdTimestamp);
        persistContractTransactionHash(hash, consensusTimestamp, payerId, contractId);
        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(consensusTimestamp - 1L)
                        .parentConsensusTimestamp(consensusTimestamp)
                        .entityId(failedAccount)
                        .nonce(1)
                        .type(CRYPTOCREATEACCOUNT.getProtoId())
                        .result(ResponseCodeEnum.INVALID_SIGNATURE.getNumber()))
                .persist();
        domainBuilder
                .contractAction()
                .customize(a -> a.consensusTimestamp(consensusTimestamp)
                        .caller(contractId)
                        .callerType(EntityType.CONTRACT)
                        .recipientAccount(failedAccount)
                        .value(25L)
                        .index(0))
                .persist();

        final var response = prestateService.processPrestateCall(createRequest(hash, true, false, false));

        final var failedAddress = toLongZeroAddress(failedAccount);
        assertThat(response.getPre()).extracting(t -> t.getAddress()).doesNotContain(failedAddress);
        assertThat(response.getPost()).extracting(t -> t.getAddress()).doesNotContain(failedAddress);
    }

    @Test
    void callWithDiffEmitsDeletedAccountOnlyInPre() {
        final var payerId = domainBuilder.entityId();
        final var contractId = domainBuilder.entityId();
        final var createdTimestamp = domainBuilder.timestamp();
        final var consensusTimestamp = createdTimestamp + 100;
        final var hash = domainBuilder.bytes(32);

        domainBuilder
                .entityHistory(contractId, createdTimestamp)
                .customize(e -> e.type(EntityType.CONTRACT)
                        .ethereumNonce(1L)
                        .evmAddress(null)
                        .alias(null)
                        .deleted(false)
                        .timestampRange(Range.closedOpen(createdTimestamp, consensusTimestamp)))
                .persist();
        domainBuilder
                .entity(contractId, createdTimestamp)
                .customize(e -> e.type(EntityType.CONTRACT)
                        .ethereumNonce(1L)
                        .evmAddress(null)
                        .alias(null)
                        .deleted(true)
                        .timestampRange(Range.atLeast(consensusTimestamp)))
                .persist();
        persistTreasuryBalance(createdTimestamp);
        persistAccountBalance(contractId, createdTimestamp, 50L);
        persistContractTransactionHash(hash, consensusTimestamp, payerId, contractId);
        domainBuilder
                .contractAction()
                .customize(a -> a.consensusTimestamp(consensusTimestamp)
                        .caller(payerId)
                        .callerType(EntityType.ACCOUNT)
                        .recipientContract(contractId)
                        .value(0L)
                        .index(0))
                .persist();

        final var response = prestateService.processPrestateCall(createRequest(hash, true, false, false));

        final var deletedAddress = toLongZeroAddress(contractId);
        assertThat(response.getPre())
                .filteredOn(t -> deletedAddress.equals(t.getAddress()))
                .singleElement()
                .extracting(t -> t.getBalance())
                .isEqualTo("0x746a528800");
        assertThat(response.getPost()).extracting(t -> t.getAddress()).doesNotContain(deletedAddress);
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
        prestateProperties.setMaxTouchedAccounts(10);
        final var fixture = persistMultipleAccountsFixture(5);

        final var response = prestateService.processPrestateCall(createRequest(fixture.hash(), false, false, false));

        assertThat(response.getPre()).hasSize(5);
    }

    @Test
    void callReturnsAllAccountsWhenExactlyAtMaxLimit() {
        prestateProperties.setMaxTouchedAccounts(5);
        final var fixture = persistMultipleAccountsFixture(5);

        final var response = prestateService.processPrestateCall(createRequest(fixture.hash(), false, false, false));

        assertThat(response.getPre()).hasSize(5);
    }

    @Test
    void callLimitsAccountsWhenAboveMaxLimit() {
        prestateProperties.setMaxTouchedAccounts(3);
        final var fixture = persistMultipleAccountsFixture(5);

        final var response = prestateService.processPrestateCall(createRequest(fixture.hash(), false, false, false));

        assertThat(response.getPre()).hasSize(3);
    }

    @Test
    void callSkipsSubsequentActionsOnceAccountCapReached() {
        // Two actions where each contributes 3 distinct entities (caller + recipientAccount + recipientContract).
        // The cap is expressed in accounts, so once the first action's 3 accounts are added and the size hits the
        // cap, the second action must NOT be processed at all.
        prestateProperties.setMaxTouchedAccounts(3);

        final var payerId = domainBuilder.entityId();
        final var contractId = domainBuilder.entityId();
        final var caller1 = domainBuilder.entityId();
        final var recipientAccount1 = domainBuilder.entityId();
        final var recipientContract1 = domainBuilder.entityId();
        final var caller2 = domainBuilder.entityId();
        final var recipientAccount2 = domainBuilder.entityId();
        final var recipientContract2 = domainBuilder.entityId();
        final var createdTimestamp = domainBuilder.timestamp();
        final var consensusTimestamp = createdTimestamp + 100;
        final var hash = domainBuilder.bytes(32);

        persistTreasuryBalance(createdTimestamp);
        persistBareEntity(caller1, EntityType.CONTRACT, 0L, createdTimestamp);
        persistBareEntity(recipientAccount1, EntityType.ACCOUNT, 0L, createdTimestamp);
        persistBareEntity(recipientContract1, EntityType.CONTRACT, 0L, createdTimestamp);
        persistBareEntity(caller2, EntityType.CONTRACT, 0L, createdTimestamp);
        persistBareEntity(recipientAccount2, EntityType.ACCOUNT, 0L, createdTimestamp);
        persistBareEntity(recipientContract2, EntityType.CONTRACT, 0L, createdTimestamp);

        persistContractTransactionHash(hash, consensusTimestamp, payerId, contractId);
        domainBuilder
                .contractAction()
                .customize(a -> a.consensusTimestamp(consensusTimestamp)
                        .caller(caller1)
                        .callerType(EntityType.CONTRACT)
                        .recipientAccount(recipientAccount1)
                        .recipientContract(recipientContract1)
                        .value(0L)
                        .index(0))
                .persist();
        domainBuilder
                .contractAction()
                .customize(a -> a.consensusTimestamp(consensusTimestamp)
                        .caller(caller2)
                        .callerType(EntityType.CONTRACT)
                        .recipientAccount(recipientAccount2)
                        .recipientContract(recipientContract2)
                        .value(0L)
                        .index(1))
                .persist();

        final var response = prestateService.processPrestateCall(createRequest(hash, false, false, false));

        // Only action 1's 3 accounts should be included; action 2 is skipped once size >= cap.
        assertThat(response.getPre()).hasSize(3);
    }

    private PrestateRequest createRequest(
            final byte[] hash, final boolean diff, final boolean code, final boolean storage) {
        return new PrestateRequest(new TransactionHashParameter(Bytes.of(hash)), diff, code, storage);
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
        persistBareEntity(entityId, type, nonce, createdTimestamp, null, null);
    }

    private void persistBareEntity(
            final EntityId entityId,
            final EntityType type,
            final long nonce,
            final long createdTimestamp,
            final byte[] evmAddress) {
        persistBareEntity(entityId, type, nonce, createdTimestamp, evmAddress, evmAddress);
    }

    private void persistBareEntity(
            final EntityId entityId,
            final EntityType type,
            final long nonce,
            final long createdTimestamp,
            final byte[] evmAddress,
            final byte[] alias) {
        domainBuilder
                .entity(entityId, createdTimestamp)
                .customize(e -> e.type(type)
                        .ethereumNonce(nonce)
                        .evmAddress(evmAddress)
                        .alias(alias)
                        .deleted(false)
                        .timestampRange(Range.atLeast(createdTimestamp)))
                .persist();
    }

    private void persistSuccessfulCryptoCreateChild(
            final EntityId entityId, final long parentConsensusTimestamp, final long childConsensusTimestamp) {
        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(childConsensusTimestamp)
                        .parentConsensusTimestamp(parentConsensusTimestamp)
                        .entityId(entityId)
                        .nonce(1)
                        .type(CRYPTOCREATEACCOUNT.getProtoId())
                        .result(ResponseCodeEnum.SUCCESS.getNumber()))
                .persist();
    }

    private void persistCallAction(
            final long consensusTimestamp,
            final EntityId caller,
            final EntityType callerType,
            final EntityId recipientContract,
            final long value,
            final CallOperationType operationType,
            final int callDepth) {
        domainBuilder
                .contractAction()
                .customize(a -> a.consensusTimestamp(consensusTimestamp)
                        .caller(caller)
                        .callerType(callerType)
                        .callOperationType(operationType.getNumber())
                        .callDepth(callDepth)
                        .recipientAccount(null)
                        .recipientContract(recipientContract)
                        .value(value)
                        .index(0))
                .persist();
    }

    private void persistEthereumCall(
            final byte[] hash,
            final long consensusTimestamp,
            final EntityId payerId,
            final EntityId senderId,
            final EntityId contractId,
            final long ethereumNonce,
            final byte[] functionResult,
            final List<Authorization> authorizations) {
        persistContractTransactionHash(hash, consensusTimestamp, payerId, contractId);
        domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(consensusTimestamp)
                        .payerAccountId(payerId)
                        .senderId(senderId)
                        .contractId(contractId.getId())
                        .createdContractIds(List.of())
                        .functionResult(functionResult)
                        .amount(0L))
                .persist();
        domainBuilder
                .ethereumTransaction(true)
                .customize(e -> e.consensusTimestamp(consensusTimestamp)
                        .payerAccountId(payerId)
                        .nonce(ethereumNonce)
                        .hash(hash)
                        .authorizationList(authorizations)
                        .toAddress(toEvmAddress(contractId)))
                .persist();
    }

    private static byte[] signerNonceFunctionResult(final EntityId senderId, final long signerNonce) {
        return ContractFunctionResult.newBuilder()
                .setSenderId(AccountID.newBuilder()
                        .setShardNum(senderId.getShard())
                        .setRealmNum(senderId.getRealm())
                        .setAccountNum(senderId.getNum()))
                .setSignerNonce(Int64Value.of(signerNonce))
                .build()
                .toByteArray();
    }

    private static byte[] createdContractNonceFunctionResult(final EntityId contractId, final long nonce) {
        return ContractFunctionResult.newBuilder()
                .addContractNonces(ContractNonceInfo.newBuilder()
                        .setContractId(ContractID.newBuilder()
                                .setShardNum(contractId.getShard())
                                .setRealmNum(contractId.getRealm())
                                .setContractNum(contractId.getNum()))
                        .setNonce(nonce))
                .build()
                .toByteArray();
    }

    private static Authorization signedAuthorization(final KeyPair keyPair, final byte[] target, final long nonce) {
        final var unsigned = new CodeDelegation(new byte[] {0}, target, nonce, 0, new byte[] {1}, new byte[] {1});
        final var message = unsigned.calculateSignableMessage();
        final var hash = Bytes32.wrap(new Keccak.Digest256().digest(message));
        final var signature = SECP256K1.sign(hash, keyPair);
        final var hex = HexFormat.of();
        return Authorization.builder()
                .chainId("0x0")
                .address("0x" + hex.formatHex(target))
                .nonce(nonce)
                .yParity(signature.getRecId() == 0 ? "0x0" : "0x1")
                .r("0x" + hex.formatHex(toUnsigned32(signature.getR())))
                .s("0x" + hex.formatHex(toUnsigned32(signature.getS())))
                .build();
    }

    private static byte[] evmAddressFromKeyPair(final KeyPair keyPair) {
        final var compressed =
                keyPair.getPublicKey().asEcPoint(EC_DOMAIN_PARAMETERS).getEncoded(true);
        return SignatureUtils.recoverAddressFromPubKey(compressed);
    }

    private static byte[] toUnsigned32(final BigInteger value) {
        final var hex = value.toString(16);
        final var padded = hex.length() >= 64 ? hex.substring(hex.length() - 64) : "0".repeat(64 - hex.length()) + hex;
        return HexFormat.of().parseHex(padded);
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
                            .caller(accountId)
                            .callerType(EntityType.CONTRACT)
                            .recipientAccount(null)
                            .recipientContract(null)
                            .value(0L)
                            .index(index))
                    .persist();
        }

        return new Fixture(hash, createdTimestamp, consensusTimestamp, payerId, contractId, null);
    }

    private String toLongZeroAddress(final EntityId entityId) {
        return "0x" + bytesToHex(toEvmAddress(entityId));
    }

    private static String rawHex(final byte[] bytes) {
        return Bytes.wrap(bytes).toHexString();
    }

    private record Fixture(
            byte[] hash,
            long createdTimestamp,
            long consensusTimestamp,
            EntityId payerId,
            EntityId contractId,
            EntityId accountId) {}
}
