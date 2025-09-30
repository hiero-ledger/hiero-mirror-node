// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.domain;

import static com.hedera.services.stream.proto.ContractAction.RecipientCase.RECIPIENT_NOT_SET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.entity.EntityType.ACCOUNT;
import static org.hiero.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.hiero.mirror.importer.domain.StreamFilename.FileType.DATA;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.hedera.services.stream.proto.CallOperationType;
import com.hedera.services.stream.proto.ContractActionType;
import com.hedera.services.stream.proto.ContractActions;
import com.hedera.services.stream.proto.ContractBytecode;
import com.hedera.services.stream.proto.ContractStateChanges;
import com.hedera.services.stream.proto.StorageChange;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractNonceInfo;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.common.domain.contract.Contract;
import org.hiero.mirror.common.domain.contract.ContractAction;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.contract.ContractState;
import org.hiero.mirror.common.domain.contract.ContractStateChange;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.parser.domain.RecordItemBuilder;
import org.hiero.mirror.importer.parser.record.RecordStreamFileListener;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hiero.mirror.importer.repository.ContractActionRepository;
import org.hiero.mirror.importer.repository.ContractLogRepository;
import org.hiero.mirror.importer.repository.ContractRepository;
import org.hiero.mirror.importer.repository.ContractResultRepository;
import org.hiero.mirror.importer.repository.ContractStateChangeRepository;
import org.hiero.mirror.importer.repository.ContractStateRepository;
import org.hiero.mirror.importer.repository.EntityRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.util.Version;
import org.springframework.transaction.support.TransactionTemplate;

@RequiredArgsConstructor
final class ContractResultServiceImplIntegrationTest extends ImporterIntegrationTest {

    private final ContractRepository contractRepository;
    private final ContractActionRepository contractActionRepository;
    private final ContractLogRepository contractLogRepository;
    private final ContractResultRepository contractResultRepository;
    private final ContractResultService contractResultService;
    private final ContractStateChangeRepository contractStateChangeRepository;
    private final ContractStateRepository contractStateRepository;
    private final EntityRepository entityRepository;

    private final EntityProperties entityProperties;
    private final RecordItemBuilder recordItemBuilder;
    private final RecordStreamFileListener recordStreamFileListener;
    private final SecureRandom secureRandom = new SecureRandom();
    private final TransactionTemplate transactionTemplate;

    private Transaction transaction;

    @AfterEach
    void cleanup() {
        entityProperties.getPersist().setTrackNonce(true);
    }

    @Test
    void processContractCall() {
        RecordItem recordItem = recordItemBuilder.contractCall().build();

        process(recordItem);

        assertContractResult(recordItem);
        assertContractLogs(recordItem);
        assertContractActions(recordItem);
        assertContractStateChanges(recordItem);
        assertThat(contractRepository.count()).isZero();
        assertThat(entityRepository.count()).isZero();
    }

    @SuppressWarnings("deprecation")
    @Test
    void processContractCreate() {
        RecordItem recordItem = recordItemBuilder.contractCreate().build();
        ContractFunctionResult contractFunctionResult =
                recordItem.getTransactionRecord().getContractCreateResult();
        var transactionBody = recordItem.getTransactionBody().getContractCreateInstance();
        var entityId =
                EntityId.of(contractFunctionResult.getCreatedContractIDs(0)).getId();

        process(recordItem);

        assertContractResult(recordItem);
        assertContractLogs(recordItem);
        assertContractActions(recordItem);
        assertContractStateChanges(recordItem);
        assertThat(contractRepository.findAll())
                .hasSize(1)
                .first()
                .returns(EntityId.of(transactionBody.getFileID()), Contract::getFileId)
                .returns(entityId, Contract::getId);
        assertThat(entityRepository.findAll())
                .hasSize(1)
                .first()
                .returns(transactionBody.getAutoRenewPeriod().getSeconds(), Entity::getAutoRenewPeriod)
                .returns(0L, Entity::getBalance)
                .returns(recordItem.getConsensusTimestamp(), Entity::getBalanceTimestamp)
                .returns(recordItem.getConsensusTimestamp(), Entity::getCreatedTimestamp)
                .returns(false, Entity::getDeleted)
                .returns(entityId, Entity::getId)
                .returns(transactionBody.getAdminKey().toByteArray(), Entity::getKey)
                .returns(EntityId.of(transactionBody.getProxyAccountID()), Entity::getProxyAccountId)
                .returns(0, Entity::getMaxAutomaticTokenAssociations)
                .returns(transactionBody.getMemo(), Entity::getMemo);
    }

    @Test
    void processContractCreateNoChildren() {
        var recordItem = recordItemBuilder
                .contractCreate()
                .recordItem(r -> r.hapiVersion(new Version(0, 24, 0)))
                .build();

        process(recordItem);

        assertContractResult(recordItem);
        assertContractLogs(recordItem);
        assertContractActions(recordItem);
        assertContractStateChanges(recordItem);
        assertThat(contractRepository.count()).isZero();
        assertThat(entityRepository.count()).isZero();
    }

    @SuppressWarnings("deprecation")
    @ParameterizedTest
    @CsvSource({"true,2", "false,1"})
    void processContractNonce(boolean isTrackNonce, long isNonce) {
        entityProperties.getPersist().setTrackNonce(isTrackNonce);
        // given
        var entity = domainBuilder.entity().customize(c -> c.type(CONTRACT)).persist();
        domainBuilder.contract().customize(c -> c.id(entity.getId())).persist();
        var contractId = entity.toEntityId().toContractID();
        RecordItem recordItem = recordItemBuilder
                .contractCall(contractId)
                .record(r -> r.clearContractCallResult()
                        .setContractCallResult(recordItemBuilder
                                .contractFunctionResult(contractId)
                                .clearCreatedContractIDs()
                                .clearContractNonces()
                                .addContractNonces(ContractNonceInfo.newBuilder()
                                        .setContractId(contractId)
                                        .setNonce(2L))))
                .build();

        process(recordItem);

        assertContractResult(recordItem);
        assertContractLogs(recordItem);
        assertContractActions(recordItem);
        assertContractStateChanges(recordItem);

        assertThat(contractRepository.count()).isEqualTo(1);
        assertThat(entityRepository.findAll())
                .hasSize(1)
                .first()
                .returns(entity.getId(), Entity::getId)
                .returns(isNonce, Entity::getEthereumNonce);
    }

    @Test
    void processEthereumTransactionCall() {
        var ethereumTransaction = domainBuilder.ethereumTransaction(true).get();
        var recordItem = recordItemBuilder
                .ethereumTransaction(false)
                .recordItem(r -> r.ethereumTransaction(ethereumTransaction))
                .build();

        process(recordItem);

        assertContractResult(recordItem);
        assertContractLogs(recordItem);
        assertContractActions(recordItem);
        assertContractStateChanges(recordItem);
        assertThat(contractRepository.count()).isZero();
        assertThat(entityRepository.count()).isZero();
    }

    @Test
    void processEthereumTransactionCreate() {
        var ethereumTransaction = domainBuilder.ethereumTransaction(true).get();
        var recordItem = recordItemBuilder
                .ethereumTransaction(true)
                .recordItem(r -> r.ethereumTransaction(ethereumTransaction))
                .build();

        process(recordItem);

        assertContractResult(recordItem);
        assertContractLogs(recordItem);
        assertContractActions(recordItem);
        assertContractStateChanges(recordItem);
        assertThat(contractRepository.count()).isZero();
        assertThat(entityRepository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void processFailedEthereumTransactionWithoutContractResult(boolean hasInitCode) {
        var ethereumTransaction = domainBuilder.ethereumTransaction(hasInitCode).get();
        var recordItem = recordItemBuilder
                .ethereumTransaction()
                .recordItem(r -> r.ethereumTransaction(ethereumTransaction))
                .record(r -> r.clearContractCallResult().clearContractCreateResult())
                .receipt(r -> r.setStatus(ResponseCodeEnum.CONSENSUS_GAS_EXHAUSTED))
                .sidecarRecords(List::clear)
                .build();

        process(recordItem);

        var expectedFunctionParameters = hasInitCode ? ethereumTransaction.getCallData() : DomainUtils.EMPTY_BYTE_ARRAY;
        var expected = ContractResult.builder()
                .callResult(DomainUtils.EMPTY_BYTE_ARRAY)
                .consensusTimestamp(recordItem.getConsensusTimestamp())
                .contractId(0)
                .functionParameters(expectedFunctionParameters)
                .gasLimit(ethereumTransaction.getGasLimit())
                .gasUsed(0L)
                .payerAccountId(recordItem.getPayerAccountId())
                .senderId(recordItem.getPayerAccountId())
                .transactionHash(ethereumTransaction.getHash())
                .transactionIndex(recordItem.getTransactionIndex())
                .transactionNonce(
                        recordItem.getTransactionRecord().getTransactionID().getNonce())
                .transactionResult(ResponseCodeEnum.CONSENSUS_GAS_EXHAUSTED_VALUE)
                .build();
        assertThat(contractResultRepository.findAll()).containsExactly(expected);
        assertThat(contractLogRepository.count()).isZero();
        assertThat(contractActionRepository.count()).isZero();
        assertThat(contractStateChangeRepository.count()).isZero();
        assertThat(contractRepository.count()).isZero();
        assertThat(entityRepository.count()).isZero();
    }

    @ParameterizedTest
    @EnumSource(
            value = ResponseCodeEnum.class,
            names = {"DUPLICATE_TRANSACTION", "WRONG_NONCE"})
    void processFailedEthereumTransactionNoDefaultContractResult(ResponseCodeEnum status) {
        var ethereumTransaction = domainBuilder.ethereumTransaction(false).get();
        var recordItem = recordItemBuilder
                .ethereumTransaction()
                .recordItem(r -> r.ethereumTransaction(ethereumTransaction))
                .record(r -> r.clearContractCallResult().clearContractCreateResult())
                .receipt(r -> r.setStatus(status))
                .sidecarRecords(List::clear)
                .build();

        process(recordItem);

        assertThat(contractResultRepository.count()).isZero();
        assertThat(contractLogRepository.count()).isZero();
        assertThat(contractActionRepository.count()).isZero();
        assertThat(contractStateChangeRepository.count()).isZero();
        assertThat(contractRepository.count()).isZero();
        assertThat(entityRepository.count()).isZero();
    }

    @Test
    void processContractActionInvalidCaller() {
        var recordItem = recordItemBuilder
                .contractCall()
                .sidecarRecords(s -> s.get(1)
                        .getActionsBuilder()
                        .getContractActionsBuilder(0)
                        .setCallingContract(ContractID.newBuilder()
                                .setShardNum(1819278731L)
                                .setRealmNum(-1L)
                                .setContractNum(-1L)))
                .sidecarRecords(s -> s.get(1)
                        .getActionsBuilder()
                        .removeContractActions(2)
                        .getContractActionsBuilder(1)
                        .setCallingAccount(AccountID.newBuilder()
                                .setShardNum(1819278731L)
                                .setRealmNum(-1L)
                                .setAccountNum(-1L)))
                .build();

        process(recordItem);

        assertContractResult(recordItem);
        assertContractLogs(recordItem);
        assertContractActions(recordItem);
        assertContractStateChanges(recordItem);
        assertThat(contractRepository.count()).isZero();
        assertThat(entityRepository.count()).isZero();
        assertThat(contractActionRepository.findAll())
                .allSatisfy(a -> assertThat(a.getCallerType()).isNotNull())
                .allSatisfy(a -> assertThat(a.getCaller()).isNull());
    }

    @Test
    void processContractActionMissingCaller() {
        var recordItem = recordItemBuilder
                .contractCall()
                .sidecarRecords(s -> s.get(1)
                        .getActionsBuilder()
                        .getContractActionsBuilder(0)
                        .clearCaller())
                .build();

        process(recordItem);

        assertContractResult(recordItem);
        assertContractLogs(recordItem);
        assertContractActions(recordItem);
        assertContractStateChanges(recordItem);
        assertThat(contractRepository.count()).isZero();
        assertThat(entityRepository.count()).isZero();
    }

    @Test
    void processGasConsumedCalculationContractCall() {
        // Given RecordItem with 3 contract actions
        final var recordItem = recordItemBuilder.contractCall().build();

        final var contractActionRecord = TransactionSidecarRecord.newBuilder()
                .setActions(ContractActions.newBuilder()
                        .addAllContractActions(List.of(
                                contractAction(CallOperationType.OP_CALL, ContractActionType.CALL, 0),
                                contractAction(CallOperationType.OP_CREATE, ContractActionType.CREATE, 1),
                                contractAction(CallOperationType.OP_DELEGATECALL, ContractActionType.CALL, 2))))
                .build();

        recordItem.setSidecarRecords(List.of(contractActionRecord));

        // When
        process(recordItem);

        // Then
        assertThat(contractResultRepository.findById(recordItem.getConsensusTimestamp()))
                .get()
                .returns(22074L, ContractResult::getGasConsumed);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void processGasConsumedCalculationContractCreate(boolean blockstream) {
        // Given RecordItem with 2 contract actions and bytecode sidecar record
        final var recordItem = recordItemBuilder
                .contractCreate()
                .recordItem(r -> r.blockstream(blockstream))
                .build();

        final var contractActionRecord = TransactionSidecarRecord.newBuilder()
                .setActions(ContractActions.newBuilder()
                        .addAllContractActions(List.of(
                                contractAction(CallOperationType.OP_CREATE, ContractActionType.CREATE, 0),
                                contractAction(CallOperationType.OP_CALL, ContractActionType.CALL, 1))))
                .build();

        final var initcode = new byte[] {1, 0, 0, 0, 0, 1, 1, 1, 1};
        final var bytecodeRecord = TransactionSidecarRecord.newBuilder()
                .setBytecode(ContractBytecode.newBuilder()
                        .setInitcode(blockstream ? ByteString.EMPTY : DomainUtils.fromBytes(initcode)))
                .build();

        if (blockstream) {
            final var fileId = EntityId.of(
                    recordItem.getTransactionBody().getContractCreateInstance().getFileID());
            domainBuilder
                    .fileData()
                    .customize(f -> f.consensusTimestamp(recordItem.getConsensusTimestamp() - 1)
                            .entityId(fileId)
                            .fileData(initcode))
                    .persist();
        }

        recordItem.setSidecarRecords(List.of(contractActionRecord, bytecodeRecord));

        // When
        process(recordItem);

        // Then
        assertThat(contractResultRepository.findById(recordItem.getConsensusTimestamp()))
                .get()
                .returns(53146L, ContractResult::getGasConsumed);
    }

    @Test
    void processGasConsumedCalculationNullCase() {
        // Given
        final var recordItem = recordItemBuilder.contractCall().build();
        recordItem.setSidecarRecords(List.of());

        // When
        process(recordItem);

        // Then
        final var contractResult = contractResultRepository
                .findById(recordItem.getConsensusTimestamp())
                .orElse(null);

        assertThat(contractResult)
                .isNotNull()
                .extracting(ContractResult::getGasConsumed)
                .isNull();
    }

    @Test
    void processContractActionInvalidRecipient() {
        var recordItem = recordItemBuilder
                .contractCall()
                .sidecarRecords(s -> s.get(1)
                        .getActionsBuilder()
                        .getContractActionsBuilder(0)
                        .setRecipientContract(ContractID.newBuilder()
                                .setShardNum(1819278731L)
                                .setRealmNum(-1L)
                                .setContractNum(-1L)))
                .sidecarRecords(s -> s.get(1)
                        .getActionsBuilder()
                        .removeContractActions(2)
                        .getContractActionsBuilder(1)
                        .setRecipientAccount(AccountID.newBuilder()
                                .setShardNum(1819278731L)
                                .setRealmNum(-1L)
                                .setAccountNum(-1L)))
                .build();

        process(recordItem);

        assertContractResult(recordItem);
        assertContractLogs(recordItem);
        assertContractStateChanges(recordItem);
        assertThat(contractRepository.count()).isZero();
        assertThat(entityRepository.count()).isZero();
        assertThat(contractActionRepository.findAll()).allSatisfy(a -> assertThat(a)
                .returns(null, ContractAction::getRecipientAccount)
                .returns(null, ContractAction::getRecipientContract)
                .returns(null, ContractAction::getRecipientAddress));
    }

    @Test
    void processContractActionInvalidResultData() {
        var recordItem = recordItemBuilder
                .contractCall()
                .sidecarRecords(s -> s.get(1)
                        .getActionsBuilder()
                        .getContractActionsBuilder(0)
                        .clearResultData())
                .build();

        process(recordItem);

        assertContractResult(recordItem);
        assertContractLogs(recordItem);
        assertContractActions(recordItem);
        assertContractStateChanges(recordItem);
        assertThat(contractRepository.count()).isZero();
        assertThat(entityRepository.count()).isZero();
    }

    @Test
    void processPrecompile() {
        RecordItem recordItem = recordItemBuilder
                .tokenMint(TokenType.FUNGIBLE_COMMON)
                .record(x -> x.setContractCallResult(recordItemBuilder.contractFunctionResult()))
                .build();

        process(recordItem);

        assertContractResult(recordItem);
        assertContractLogs(recordItem);
        assertThat(contractActionRepository.count()).isZero();
        assertThat(contractStateChangeRepository.count()).isZero();
        assertThat(contractStateRepository.count()).isZero();
        assertThat(contractRepository.count()).isZero();
    }

    @Test
    void processContractCallDefaultFunctionResult() {
        RecordItem recordItem = recordItemBuilder
                .contractCall()
                .record(TransactionRecord.Builder::clearContractCallResult)
                .build();

        process(recordItem);

        assertContractResult(recordItem);
        assertContractLogs(recordItem);
        assertContractActions(recordItem);
        assertContractStateChanges(recordItem);
        assertThat(contractRepository.count()).isZero();
        assertThat(entityRepository.count()).isZero();
    }

    @Test
    void processContractCreateDefaultFunctionResult() {
        RecordItem recordItem = recordItemBuilder
                .contractCreate()
                .record(TransactionRecord.Builder::clearContractCreateResult)
                .build();

        process(recordItem);

        assertContractResult(recordItem);
        assertContractLogs(recordItem);
        assertContractActions(recordItem);
        assertContractStateChanges(recordItem);
        assertThat(contractRepository.count()).isZero();
        assertThat(entityRepository.count()).isZero();
    }

    @Test
    void processNoContractLogs() {
        RecordItem recordItem = recordItemBuilder
                .contractCall()
                .record(x -> x.getContractCreateResultBuilder().clearLogInfo())
                .build();

        process(recordItem);

        assertContractResult(recordItem);
        assertContractActions(recordItem);
        assertContractStateChanges(recordItem);
        assertThat(contractLogRepository.count()).isZero();
    }

    @Test
    void processNoSidecars() {
        RecordItem recordItem =
                recordItemBuilder.contractCreate().sidecarRecords(List::clear).build();

        process(recordItem);

        assertContractResult(recordItem);
        assertContractLogs(recordItem);
        assertContractStateChanges(recordItem);
        assertThat(contractActionRepository.count()).isZero();
        assertThat(contractStateChangeRepository.count()).isZero();
        assertThat(contractStateRepository.count()).isZero();
    }

    @Test
    void processContractCallFailure() {
        RecordItem recordItem = recordItemBuilder
                .contractCall()
                .record(TransactionRecord.Builder::clearContractCallResult)
                .receipt(r -> r.clearContractID().setStatus(ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION))
                .build();

        process(recordItem);

        assertContractResult(recordItem);
        assertContractLogs(recordItem);
        assertContractActions(recordItem);
        assertContractStateChanges(recordItem);
        assertThat(contractRepository.count()).isZero();
        assertThat(entityRepository.count()).isZero();
    }

    @Test
    void processContractCreateFailure() {
        RecordItem recordItem = recordItemBuilder
                .contractCreate()
                .transactionBody(t -> t.setInitcode(ByteString.copyFrom(new byte[] {9, 8, 7})))
                .record(TransactionRecord.Builder::clearContractCreateResult)
                .receipt(r -> r.clearContractID().setStatus(ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION))
                .build();

        process(recordItem);

        assertContractResult(recordItem);
        assertContractLogs(recordItem);
        assertContractActions(recordItem);
        assertContractStateChanges(recordItem);
        assertThat(contractRepository.count()).isZero();
        assertThat(entityRepository.count()).isZero();
    }

    @Test
    void processSidecarContractCreateFailure() {
        RecordItem recordItem = recordItemBuilder
                .contractCreate()
                .record(TransactionRecord.Builder::clearContractCreateResult)
                .receipt(r -> r.clearContractID().setStatus(ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION))
                .build();

        process(recordItem);

        assertContractResult(recordItem);
        assertContractLogs(recordItem);
        assertContractActions(recordItem);
        assertContractStateChanges(recordItem);
        assertThat(contractRepository.count()).isZero();
        assertThat(entityRepository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void migrateContractSidecar(boolean migration) {
        // given
        var entity1 = domainBuilder.entity().persist();
        var entity2 = domainBuilder.entity().persist();
        var entity3 = domainBuilder.entity().persist();
        domainBuilder.contract().customize(c -> c.id(entity1.getId())).persist();
        domainBuilder
                .contract()
                .customize(c -> c.id(entity2.getId()).runtimeBytecode(null))
                .persist();
        domainBuilder.contract().customize(c -> c.id(entity3.getId())).persist();

        var recordItem = recordItemBuilder.cryptoTransfer().build();
        var stateChangeRecord1 = TransactionSidecarRecord.newBuilder()
                .setMigration(migration)
                .setStateChanges(ContractStateChanges.newBuilder()
                        .addContractStateChanges(com.hedera.services.stream.proto.ContractStateChange.newBuilder()
                                .setContractId(domainBuilder.entityId().toContractID())
                                .addStorageChanges(StorageChange.newBuilder()
                                        .setValueWritten(BytesValue.of(DomainUtils.fromBytes(new byte[] {1, 1})))))
                        .addContractStateChanges(com.hedera.services.stream.proto.ContractStateChange.newBuilder()
                                .setContractId(domainBuilder.entityId().toContractID())
                                .addStorageChanges(StorageChange.newBuilder()
                                        .setValueWritten(BytesValue.of(DomainUtils.fromBytes(new byte[] {2, 2}))))))
                .build();
        var stateChangeRecord2 = TransactionSidecarRecord.newBuilder()
                .setMigration(false)
                .setStateChanges(ContractStateChanges.newBuilder()
                        .addContractStateChanges(com.hedera.services.stream.proto.ContractStateChange.newBuilder()
                                .setContractId(domainBuilder.entityId().toContractID())
                                .addStorageChanges(StorageChange.newBuilder()
                                        .setValueWritten(BytesValue.of(DomainUtils.fromBytes(new byte[] {3, 3})))))
                        .addContractStateChanges(com.hedera.services.stream.proto.ContractStateChange.newBuilder()
                                .setContractId(domainBuilder.entityId().toContractID())
                                .addStorageChanges(StorageChange.newBuilder()
                                        .setValueWritten(BytesValue.of(DomainUtils.fromBytes(new byte[] {4, 4}))))))
                .build();
        var bytecodeRecord1 = TransactionSidecarRecord.newBuilder()
                .setMigration(migration)
                .setBytecode(ContractBytecode.newBuilder()
                        .setContractId(entity1.toEntityId().toContractID())
                        .setRuntimeBytecode(ByteString.copyFrom(new byte[] {1})))
                .build();
        var bytecodeRecord2 = TransactionSidecarRecord.newBuilder()
                .setMigration(migration)
                .setBytecode(ContractBytecode.newBuilder()
                        .setContractId(entity2.toEntityId().toContractID())
                        .setRuntimeBytecode(ByteString.copyFrom(new byte[] {2})))
                .build();
        var bytecodeRecord3 = TransactionSidecarRecord.newBuilder()
                .setMigration(false)
                .setBytecode(ContractBytecode.newBuilder()
                        .setContractId(entity3.toEntityId().toContractID())
                        .setRuntimeBytecode(ByteString.copyFrom(new byte[] {3})))
                .build();

        var contractActionRecord = TransactionSidecarRecord.newBuilder()
                .setActions(ContractActions.newBuilder()
                        .addAllContractActions(List.of(
                                contractAction(CallOperationType.OP_CALL, ContractActionType.CALL, 0),
                                contractAction(CallOperationType.OP_CREATE, ContractActionType.CREATE, 1),
                                contractAction(CallOperationType.OP_DELEGATECALL, ContractActionType.CALL, 2))))
                .build();

        recordItem.setSidecarRecords(List.of(
                stateChangeRecord1,
                stateChangeRecord2,
                bytecodeRecord1,
                bytecodeRecord2,
                bytecodeRecord3,
                contractActionRecord));

        // when
        process(recordItem);

        // then
        assertContractStateChanges(recordItem);
        assertContractRuntimeBytecode(recordItem);
        assertEntityContractType(recordItem);
    }

    private void assertEntityContractType(RecordItem recordItem) {
        var expected = recordItem.getSidecarRecords().stream()
                .filter(TransactionSidecarRecord::getMigration)
                .filter(TransactionSidecarRecord::hasBytecode)
                .map(TransactionSidecarRecord::getBytecode)
                .map(b -> EntityId.of(b.getContractId()).getId())
                .toList();
        assertThat(entityRepository.findAll())
                .filteredOn(e -> e.getType() == CONTRACT)
                .map(Entity::getId)
                .containsAll(expected);
    }

    @SuppressWarnings("deprecation")
    private void assertContractResult(RecordItem recordItem) {
        var functionResult = getFunctionResult(recordItem);
        var createdIds = functionResult.getCreatedContractIDsList().stream()
                .map(x -> EntityId.of(x).getId())
                .toList();
        var failedInitcode = getFailedInitcode(recordItem);
        var hash = getTransactionHash(recordItem);

        assertThat(contractResultRepository.findAll())
                .hasSize(1)
                .first()
                .returns(recordItem.getConsensusTimestamp(), ContractResult::getConsensusTimestamp)
                .returns(recordItem.getPayerAccountId(), ContractResult::getPayerAccountId)
                .returns(toBytes(functionResult.getBloom()), ContractResult::getBloom)
                .returns(toBytes(functionResult.getContractCallResult()), ContractResult::getCallResult)
                .returns(createdIds, ContractResult::getCreatedContractIds)
                .returns(parseContractResultStrings(functionResult.getErrorMessage()), ContractResult::getErrorMessage)
                .returns(parseContractResultLongs(functionResult.getGasUsed()), ContractResult::getGasUsed)
                .returns(toBytes(failedInitcode), ContractResult::getFailedInitcode)
                .returns(transaction.getIndex(), ContractResult::getTransactionIndex)
                .returns(transaction.getNonce(), ContractResult::getTransactionNonce)
                .returns(transaction.getResult(), ContractResult::getTransactionResult)
                .extracting(ContractResult::getTransactionHash, InstanceOfAssertFactories.BYTE_ARRAY)
                .hasSize(32)
                .isEqualTo(hash);
    }

    private void assertContractActions(RecordItem recordItem) {
        var expected = recordItem.getSidecarRecords().stream()
                .map(TransactionSidecarRecord::getActions)
                .map(actions -> {
                    var actionsMap = new HashMap<ContractAction.Id, com.hedera.services.stream.proto.ContractAction>();
                    for (int i = 0; i < actions.getContractActionsCount(); i++) {
                        var action = actions.getContractActions(i);
                        actionsMap.put(new ContractAction.Id(recordItem.getConsensusTimestamp(), i), action);
                    }
                    return actionsMap;
                })
                .flatMap(actionsMap -> actionsMap.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        assertThat(contractActionRepository.findAll())
                .hasSize(expected.size())
                .allSatisfy(a -> assertAll(() -> assertThat(expected.get(a.getId()))
                        .isNotNull()
                        .returns(a.getCallDepth(), e -> e.getCallDepth())
                        .returns(a.getCallOperationType(), e -> e.getCallOperationTypeValue())
                        .returns(a.getCallType(), e -> e.getCallTypeValue())
                        .returns(a.getConsensusTimestamp(), e -> recordItem.getConsensusTimestamp())
                        .returns(a.getGas(), e -> e.getGas())
                        .returns(a.getGasUsed(), e -> e.getGasUsed())
                        .returns(a.getCallerType(), this::getExpectedCallerType)
                        .returns(a.getPayerAccountId(), e -> recordItem.getPayerAccountId())
                        .returns(a.getResultDataType(), e -> e.getResultDataCase()
                                .getNumber())
                        .returns(a.getValue(), e -> e.getValue())
                        .satisfiesAnyOf(
                                e -> assertThat(a.getRecipientContract()).isNotNull(),
                                e -> assertThat(a.getRecipientAccount()).isNotNull(),
                                e -> assertThat(a.getRecipientAddress()).isNotEmpty(),
                                e -> assertThat(e.getRecipientCase()).isEqualTo(RECIPIENT_NOT_SET))));
    }

    private EntityType getExpectedCallerType(com.hedera.services.stream.proto.ContractAction e) {
        return switch (e.getCallerCase()) {
            case CALLING_CONTRACT -> CONTRACT;
            case CALLING_ACCOUNT -> ACCOUNT;
            default -> null;
        };
    }

    private void assertContractRuntimeBytecode(RecordItem recordItem) {
        var expected = recordItem.getSidecarRecords().stream()
                .filter(TransactionSidecarRecord::getMigration)
                .filter(TransactionSidecarRecord::hasBytecode)
                .map(TransactionSidecarRecord::getBytecode)
                .map(b -> Contract.builder()
                        .id(EntityId.of(b.getContractId()).getId())
                        .runtimeBytecode(DomainUtils.toBytes(b.getRuntimeBytecode()))
                        .build())
                .toList();
        assertThat(contractRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorOnFields("id", "runtimeBytecode")
                .containsAll(expected);
    }

    private byte[] getTransactionHash(RecordItem recordItem) {
        return recordItem.getEthereumTransaction() == null
                ? Arrays.copyOfRange(transaction.getTransactionHash(), 0, 32)
                : recordItem.getEthereumTransaction().getHash();
    }

    private void assertContractLogs(RecordItem recordItem) {
        var contractFunctionResult = getFunctionResult(recordItem);
        var listAssert = assertThat(contractLogRepository.findAll()).hasSize(contractFunctionResult.getLogInfoCount());
        var transactionHash = getTransactionHash(recordItem);
        var transactionIndex = recordItem.getTransactionIndex();

        if (contractFunctionResult.getLogInfoCount() > 0) {
            var blooms = new ArrayList<byte[]>();
            var contractIds = new ArrayList<EntityId>();
            var data = new ArrayList<byte[]>();
            contractFunctionResult.getLogInfoList().forEach(x -> {
                blooms.add(DomainUtils.toBytes(x.getBloom()));
                contractIds.add(EntityId.of(x.getContractID()));
                data.add(DomainUtils.toBytes(x.getData()));
            });

            listAssert.extracting(ContractLog::getPayerAccountId).containsOnly(recordItem.getPayerAccountId());
            listAssert.extracting(ContractLog::getContractId).containsAll(contractIds);
            listAssert
                    .extracting(ContractLog::getRootContractId)
                    .containsOnly(EntityId.of(contractFunctionResult.getContractID()));
            listAssert.extracting(ContractLog::getConsensusTimestamp).containsOnly(recordItem.getConsensusTimestamp());
            listAssert.extracting(ContractLog::getIndex).containsExactlyInAnyOrder(0, 1);
            listAssert.extracting(ContractLog::getBloom).containsAll(blooms);
            listAssert.extracting(ContractLog::getData).containsAll(data);
            listAssert.extracting(ContractLog::getTransactionHash).containsOnly(transactionHash);
            listAssert.extracting(ContractLog::getTransactionIndex).containsOnly(transactionIndex);
        }
    }

    private void assertContractStateChanges(RecordItem recordItem) {
        var contractStateChanges = recordItem.getSidecarRecords().stream()
                .filter(TransactionSidecarRecord::hasStateChanges)
                .flatMap(r -> r.getStateChanges().getContractStateChangesList().stream()
                        .flatMap(protoStateChange -> {
                            var template = ContractStateChange.builder()
                                    .consensusTimestamp(recordItem.getConsensusTimestamp())
                                    .contractId(EntityId.of(protoStateChange.getContractId())
                                            .getId())
                                    .migration(r.getMigration())
                                    .payerAccountId(recordItem.getPayerAccountId())
                                    .build();
                            return protoStateChange.getStorageChangesList().stream()
                                    .map(storageChange -> template.toBuilder()
                                            .slot(DomainUtils.toBytes(storageChange.getSlot()))
                                            .valueRead(DomainUtils.toBytes(storageChange.getValueRead()))
                                            .valueWritten(DomainUtils.toBytes(storageChange
                                                    .getValueWritten()
                                                    .getValue()))
                                            .build());
                        }))
                .toList();

        var contractStates = contractStateChanges.stream()
                .filter(c -> c.getValueWritten() != null)
                .map(c -> ContractState.builder()
                        .contractId(c.getContractId())
                        .createdTimestamp(c.getConsensusTimestamp())
                        .modifiedTimestamp(c.getConsensusTimestamp())
                        .slot(DomainUtils.leftPadBytes(c.getSlot(), 32))
                        .value(c.getValueWritten())
                        .build())
                .toList();

        assertThat(contractStateChangeRepository.findAll()).containsExactlyInAnyOrderElementsOf(contractStateChanges);
        assertThat(contractStateRepository.findAll()).containsExactlyInAnyOrderElementsOf(contractStates);
    }

    protected void process(RecordItem recordItem) {
        var entityId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(recordItem.getConsensusTimestamp())
                        .entityId(entityId)
                        .payerAccountId(recordItem.getPayerAccountId())
                        .result(recordItem.getTransactionRecord().getReceipt().getStatusValue())
                        .transactionHash(DomainUtils.toBytes(
                                recordItem.getTransactionRecord().getTransactionHash()))
                        .type(recordItem.getTransactionType())
                        .validStartNs(DomainUtils.timeStampInNanos((recordItem
                                .getTransactionRecord()
                                .getTransactionID()
                                .getTransactionValidStart()))))
                .get();

        transactionTemplate.executeWithoutResult(status -> {
            Instant instant = Instant.ofEpochSecond(0, recordItem.getConsensusTimestamp());
            String filename = StreamFilename.getFilename(StreamType.RECORD, DATA, instant);
            long consensusStart = recordItem.getConsensusTimestamp();
            RecordFile recordFile = domainBuilder
                    .recordFile()
                    .customize(x -> x.consensusStart(consensusStart)
                            .consensusEnd(consensusStart + 1)
                            .name(filename))
                    .get();

            contractResultService.process(recordItem, transaction);
            // commit, close connection
            recordStreamFileListener.onEnd(recordFile);
        });
    }

    private ByteString getFailedInitcode(RecordItem recordItem) {
        if (recordItem.isSuccessful()) {
            return ByteString.EMPTY;
        }

        var failedInitcode =
                recordItem.getTransactionBody().getContractCreateInstance().getInitcode();
        if (failedInitcode.isEmpty()) {
            failedInitcode = recordItem.getSidecarRecords().stream()
                    .filter(TransactionSidecarRecord::hasBytecode)
                    .map(s -> s.getBytecode().getInitcode())
                    .findFirst()
                    .orElse(ByteString.EMPTY);
        }

        return failedInitcode;
    }

    private ContractFunctionResult getFunctionResult(RecordItem recordItem) {
        var txnRecord = recordItem.getTransactionRecord();
        return txnRecord.hasContractCreateResult()
                ? txnRecord.getContractCreateResult()
                : txnRecord.getContractCallResult();
    }

    private com.hedera.services.stream.proto.ContractAction contractAction(
            CallOperationType callOperationType, ContractActionType contractActionType, int callDepth) {
        return com.hedera.services.stream.proto.ContractAction.newBuilder()
                .setCallDepth(callDepth)
                .setCallingContract(domainBuilder.entityId().toContractID())
                .setCallOperationType(callOperationType)
                .setCallType(contractActionType)
                .setGas(100)
                .setGasUsed(50)
                .setInput(bytes(100))
                .setRecipientContract(domainBuilder.entityId().toContractID())
                .setOutput(bytes(256))
                .setValue(20)
                .build();
    }

    private byte[] toBytes(ByteString byteString) {
        return byteString == ByteString.EMPTY ? null : DomainUtils.toBytes(byteString);
    }

    private ByteString bytes(int length) {
        byte[] bytes = randomBytes(length);
        return ByteString.copyFrom(bytes);
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    private String parseContractResultStrings(String message) {
        return StringUtils.isEmpty(message) ? null : message;
    }

    private Long parseContractResultLongs(long num) {
        return num == 0 ? null : num;
    }
}
