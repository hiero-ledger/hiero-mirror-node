// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody.Builder;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.contract.ContractTransaction;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.hook.AbstractHook;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.converter.VersionConverter;
import org.hiero.mirror.importer.migration.SidecarContractMigration;
import org.hiero.mirror.importer.parser.domain.RecordItemBuilder;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hiero.mirror.importer.parser.record.transactionhandler.EvmHookStorageHandler;
import org.hiero.mirror.importer.parser.record.transactionhandler.TransactionHandler;
import org.hiero.mirror.importer.parser.record.transactionhandler.TransactionHandlerFactory;
import org.hiero.mirror.importer.service.ContractInitcodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.util.Version;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
final class ContractResultServiceImplTest {

    private static final CommonProperties COMMON_PROPERTIES = CommonProperties.getInstance();
    private static final String RECOVERABLE_ERROR_LOG_PREFIX = "Recoverable error. ";
    private static final Version DEFAULT_SMART_CONTRACT_THROTTLING_HAPI_VERSION = Version.parse("0.67.0");

    private final RecordItemBuilder recordItemBuilder = new RecordItemBuilder();
    private final SystemEntity systemEntity = new SystemEntity(CommonProperties.getInstance());
    private final EntityProperties entityProperties = new EntityProperties(systemEntity);
    private final DomainBuilder domainBuilder = new DomainBuilder();

    @Mock
    private ContractInitcodeService contractInitcodeService;

    @Mock(strictness = LENIENT)
    private EntityIdService entityIdService;

    @Mock
    private EntityListener entityListener;

    @Mock
    private ImporterProperties importerProperties;

    @Mock
    private SidecarContractMigration sidecarContractMigration;

    @Mock
    private TransactionHandlerFactory transactionHandlerFactory;

    @Mock
    private TransactionHandler transactionHandler;

    @Mock
    private EvmHookStorageHandler evmHookStorageHandler;

    private ContractResultService contractResultService;

    private static Stream<Arguments> provideEntities() {
        Function<RecordItemBuilder, RecordItem> withDefaultContractId =
                (RecordItemBuilder builder) -> builder.tokenMint(TokenType.FUNGIBLE_COMMON)
                        .record(x -> x.setContractCallResult(
                                builder.contractFunctionResult(ContractID.getDefaultInstance())))
                        .build();
        Function<RecordItemBuilder, RecordItem> withoutDefaultContractId =
                (RecordItemBuilder builder) -> builder.tokenMint(TokenType.FUNGIBLE_COMMON)
                        .record(x -> x.setContractCallResult(builder.contractFunctionResult()))
                        .build();

        var contractIdWithEvm = ContractID.newBuilder()
                .setShardNum(COMMON_PROPERTIES.getShard())
                .setRealmNum(COMMON_PROPERTIES.getRealm())
                .setEvmAddress(ByteString.copyFromUtf8("1234"))
                .build();

        // The transaction receipt does not have an evm address, so a recoverable error is expected.
        Function<RecordItemBuilder, RecordItem> withInactiveEvmFunctionOnly =
                (RecordItemBuilder builder) -> builder.tokenMint(TokenType.FUNGIBLE_COMMON)
                        .record(x -> x.setReceipt(
                                        TransactionReceipt.newBuilder().setStatus(ResponseCodeEnum.SUCCESS))
                                .setContractCallResult(builder.contractFunctionResult(contractIdWithEvm)))
                        .build();

        // The transaction receipt has an evm address, so no recoverable error is expected.
        Function<RecordItemBuilder, RecordItem> withInactiveEvmReceipt =
                (RecordItemBuilder builder) -> builder.tokenMint(TokenType.FUNGIBLE_COMMON)
                        .record(x -> x.setReceipt(TransactionReceipt.newBuilder()
                                        .setStatus(ResponseCodeEnum.SUCCESS)
                                        .setContractID(contractIdWithEvm))
                                .setContractCallResult(builder.contractFunctionResult(contractIdWithEvm)))
                        .build();

        Function<RecordItemBuilder, RecordItem> contractCreate =
                (RecordItemBuilder builder) -> builder.contractCreate().build();

        return Stream.of(
                Arguments.of(withoutDefaultContractId, null),
                Arguments.of(withoutDefaultContractId, EntityId.EMPTY),
                Arguments.of(withDefaultContractId, null),
                Arguments.of(withDefaultContractId, EntityId.EMPTY),
                Arguments.of(contractCreate, EntityId.EMPTY),
                Arguments.of(contractCreate, null),
                Arguments.of(
                        contractCreate, EntityId.of(COMMON_PROPERTIES.getShard(), COMMON_PROPERTIES.getRealm(), 5)),
                Arguments.of(withInactiveEvmFunctionOnly, null),
                Arguments.of(withInactiveEvmFunctionOnly, EntityId.EMPTY),
                Arguments.of(withInactiveEvmReceipt, null),
                Arguments.of(withInactiveEvmReceipt, EntityId.EMPTY));
    }

    @BeforeEach
    void beforeEach() {
        doReturn(transactionHandler).when(transactionHandlerFactory).get(any(TransactionType.class));
        when(importerProperties.getSmartContractThrottlingVersion())
                .thenReturn(DEFAULT_SMART_CONTRACT_THROTTLING_HAPI_VERSION);

        contractResultService = new ContractResultServiceImpl(
                contractInitcodeService,
                entityProperties,
                entityIdService,
                entityListener,
                importerProperties,
                sidecarContractMigration,
                transactionHandlerFactory,
                evmHookStorageHandler);
    }

    @ParameterizedTest
    @MethodSource("provideEntities")
    @SneakyThrows
    void verifiesEntityLookup(
            Function<RecordItemBuilder, RecordItem> recordBuilder, EntityId entityId, CapturedOutput capturedOutput) {
        var recordItem = recordBuilder.apply(recordItemBuilder);
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.entityId(entityId).type(recordItem.getTransactionType()))
                .get();

        when(entityIdService.lookup((ContractID) any())).thenReturn(Optional.ofNullable(entityId));

        contractResultService.process(recordItem, transaction);

        verify(entityListener, times(1)).onContractResult(any());

        assertThat(capturedOutput.getAll()).doesNotContainIgnoringCase(RECOVERABLE_ERROR_LOG_PREFIX);
        verifyContractTransactions(recordItem, transaction, entityId);
    }

    private void verifyContractTransactions(RecordItem recordItem, Transaction transaction, EntityId entityId) {
        var ids = new HashSet<Long>();
        for (var sidecarRecord : recordItem.getSidecarRecords()) {
            for (var stateChange : sidecarRecord.getStateChanges().getContractStateChangesList()) {
                ids.add(EntityId.of(stateChange.getContractId()).getId());
            }
        }

        var functionResult = recordItem.getTransactionRecord().hasContractCreateResult()
                ? recordItem.getTransactionRecord().getContractCreateResult()
                : recordItem.getTransactionRecord().getContractCallResult();
        for (int index = 0; index < functionResult.getLogInfoCount(); ++index) {
            var contractLoginfo = functionResult.getLogInfo(index);
            ids.add(EntityId.of(contractLoginfo.getContractID()).getId());
        }

        var isContractCreateOrCall = recordItem.getTransactionBody().hasContractCall()
                || recordItem.getTransactionBody().hasContractCreateInstance();
        var rootId = isContractCreateOrCall ? transaction.getEntityId() : entityId;
        ids.add(Objects.requireNonNullElse(rootId, EntityId.EMPTY).getId());
        ids.add(EntityId.of(recordItem.getTransactionBody().getTransactionID().getAccountID())
                .getId());

        var idsList = new ArrayList<>(ids);
        idsList.sort(Long::compareTo);
        var contractTransactionRoot = ContractTransaction.builder()
                .consensusTimestamp(recordItem.getConsensusTimestamp())
                .contractIds(idsList)
                .payerAccountId(recordItem.getPayerAccountId().getId());
        var expectedContractTransactions = new ArrayList<ContractTransaction>();
        ids.forEach(id -> expectedContractTransactions.add(
                contractTransactionRoot.entityId(id).build()));

        var actual = recordItem.populateContractTransactions();
        actual.forEach(expectedTransaction -> {
            var sorted = expectedTransaction.getContractIds();
            sorted.sort(Long::compareTo);
            expectedTransaction.setContractIds(sorted);
        });
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expectedContractTransactions);
    }

    @ParameterizedTest
    @CsvSource({"0.67.0", "0.67.1", "0.67.0-rc.1", "0.68.0"})
    void gasConsumedWithEqualOrGreaterHapiVersionThanSmartContractThrottling(
            @ConvertWith(VersionConverter.class) Version hapiVersion) {
        // Given
        var recordItem = recordItemBuilder
                .contractCall(ContractID.newBuilder().setContractNum(1000).build())
                .recordItem(r -> r.hapiVersion(hapiVersion))
                .build();
        var transaction = domainBuilder.transaction().get();
        var contractResultCaptor = ArgumentCaptor.forClass(ContractResult.class);

        // When
        contractResultService.process(recordItem, transaction);

        // Then
        verify(entityListener, times(1)).onContractResult(contractResultCaptor.capture());
        var capturedContractResult = contractResultCaptor.getValue();
        assertThat(capturedContractResult.getGasConsumed()).isEqualTo(capturedContractResult.getGasUsed());
    }

    @Test
    void gasConsumedWithOlderHapiVersionThanSmartContractThrottling() {
        // Given
        var recordItem = recordItemBuilder
                .contractCall(ContractID.newBuilder().setContractNum(1000).build())
                .recordItem(r -> r.hapiVersion(Version.parse("0.65.0"))) // Older HAPI version
                .build();
        var transaction = domainBuilder.transaction().get();
        var contractResultCaptor = ArgumentCaptor.forClass(ContractResult.class);

        // When
        contractResultService.process(recordItem, transaction);

        // Then
        verify(entityListener, times(1)).onContractResult(contractResultCaptor.capture());
        var capturedContractResult = contractResultCaptor.getValue();
        assertThat(capturedContractResult.getGasConsumed()).isLessThan(capturedContractResult.getGasUsed());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void processHookStorageChanges_SuccessfulHookExecution(int hookCount) {
        // Given
        var parentRecordItem = recordItemBuilder.cryptoTransfer().build();
        var hookQueue = new java.util.ArrayDeque<AbstractHook.Id>();
        // Add 2 hook contexts since default contractCall creates 2 storage changes
        final var hookId = 123L;
        final var ownerId = 1001L;
        IntStream.range(0, hookCount).forEach(i -> {
            hookQueue.add(new AbstractHook.Id(hookId + i, ownerId + i));
        });
        parentRecordItem.setHookExecutionQueue(hookQueue);

        final var contractId = ContractID.newBuilder().setContractNum(365).build();
        RecordItemBuilder.Builder<Builder> recordItemBuilder = this.recordItemBuilder
                .contractCall(contractId)
                .record(r -> r.setParentConsensusTimestamp(this.recordItemBuilder.timestamp()))
                .recordItem(r -> r.parent(parentRecordItem));

        IntStream.range(0, hookCount - 1).forEach(index -> {
            recordItemBuilder.sidecarRecords(r -> r.add(this.recordItemBuilder.contractStateChanges(contractId)));
        });

        var recordItem = recordItemBuilder.build();

        when(entityIdService.lookup(any(ContractID.class))).thenReturn(Optional.of(EntityId.of(365L)));

        var transaction = domainBuilder.transaction().get();
        var hookStorageChangeCaptor =
                ArgumentCaptor.forClass(org.hiero.mirror.common.domain.hook.HookStorageChange.class);

        // When
        contractResultService.process(recordItem, transaction);

        // then
        // Verify evmHookStorageHandler.processStorageUpdatesForSidecar is called instead of direct entityListener calls
        verify(evmHookStorageHandler, times(hookCount))
                .processStorageUpdatesForSidecar(any(Long.class), any(Long.class), any(Long.class), any());
    }

    @ParameterizedTest
    @CsvSource({
        "true, true, true, true", // Valid hook execution with value written
        "true, true, true, false", // Valid hook execution without value written
        "false, false, false, true", // Not hook execution (wrong contract)
        "true, false, false, true", // Hook execution but no parent timestamp
        "true, true, false, true", // Hook execution with parent timestamp but no parent record
    })
    void processHookStorageChanges_VariousScenarios(
            boolean isHookContract, boolean hasParentTimestamp, boolean hasParentRecord, boolean hasValueWritten) {

        // Given
        var contractNum = isHookContract ? 365L : 999L;
        var recordItemBuilder_ = recordItemBuilder.contractCall(
                ContractID.newBuilder().setContractNum(contractNum).build());

        if (hasParentTimestamp) {
            recordItemBuilder_.record(r -> r.setParentConsensusTimestamp(recordItemBuilder.timestamp()));
        }

        if (hasParentRecord) {
            var parentRecordItem = recordItemBuilder.cryptoTransfer().build();
            var hookQueue = new java.util.ArrayDeque<AbstractHook.Id>();
            // Add enough hook contexts for the default 2 storage changes
            hookQueue.add(new AbstractHook.Id(1L, 1001L));
            hookQueue.add(new AbstractHook.Id(2L, 1002L));
            parentRecordItem.setHookExecutionQueue(hookQueue);
            recordItemBuilder_.recordItem(r -> r.parent(parentRecordItem));
        }

        var recordItem = recordItemBuilder_.build();

        when(entityIdService.lookup(any(ContractID.class))).thenReturn(Optional.of(EntityId.of(contractNum)));

        var transaction = domainBuilder.transaction().get();

        // When
        contractResultService.process(recordItem, transaction);

        // Then
        boolean shouldProcessHook = isHookContract && hasParentTimestamp && hasParentRecord;
        if (shouldProcessHook) {
            // Should process hook storage changes via evmHookStorageHandler
            verify(evmHookStorageHandler, times(1))
                    .processStorageUpdatesForSidecar(any(Long.class), any(Long.class), any(Long.class), any());
        } else {
            verify(evmHookStorageHandler, times(0))
                    .processStorageUpdatesForSidecar(any(Long.class), any(Long.class), any(Long.class), any());
        }
    }

    @Test
    void processHookStorageChanges_Blockstream() {
        // Child contract call has hook ID in its execution queue (from blockstream transformation)
        var childHookId = 456L;
        var childOwnerId = 789L;
        var childHookQueue = new java.util.ArrayDeque<AbstractHook.Id>();

        final var contractId = ContractID.newBuilder().setContractNum(365).build(); // Hook system contract
        var childRecordItem = recordItemBuilder
                .contractCall(contractId)
                .recordItem(r -> r.blockstream(true))
                .record(r -> r.setParentConsensusTimestamp(recordItemBuilder.timestamp()))
                .build();
        childRecordItem.setHookExecutionQueue(childHookQueue);

        when(entityIdService.lookup(any(ContractID.class))).thenReturn(Optional.of(EntityId.of(365L)));

        var transaction = domainBuilder.transaction().get();

        // Re-add the hook since nextHookContext() consumes it
        childHookQueue.add(new AbstractHook.Id(childHookId, childOwnerId));
        childRecordItem.setHookExecutionQueue(childHookQueue);

        // When - process the child contract call
        contractResultService.process(childRecordItem, transaction);

        // Then - verify that in blockstream mode, child transactions contain hook IDs
        // from the blockstream transformation and they are processed correctly

        // Verify evmHookStorageHandler was called to process the hook storage changes
        verify(evmHookStorageHandler, times(1))
                .processStorageUpdatesForSidecar(any(Long.class), any(Long.class), any(Long.class), any());

        // Verify queue is now empty after processing (hooks are consumed)
        assertThat(childRecordItem.nextHookContext()).isNull();
    }
}
