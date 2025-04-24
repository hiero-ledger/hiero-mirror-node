// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.SystemEntity;
import com.hedera.mirror.common.domain.contract.ContractTransaction;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.migration.SidecarContractMigration;
import com.hedera.mirror.importer.parser.domain.RecordItemBuilder;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.parser.record.transactionhandler.TransactionHandler;
import com.hedera.mirror.importer.parser.record.transactionhandler.TransactionHandlerFactory;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ContractResultServiceImplTest {
    private static final CommonProperties COMMON_PROPERTIES = CommonProperties.getInstance();
    private static final String RECOVERABLE_ERROR_LOG_PREFIX = "Recoverable error. ";

    private final RecordItemBuilder recordItemBuilder = new RecordItemBuilder();
    private final SystemEntity systemEntity = new SystemEntity(CommonProperties.getInstance());
    private final EntityProperties entityProperties = new EntityProperties(systemEntity);
    private final DomainBuilder domainBuilder = new DomainBuilder();

    @Mock(strictness = LENIENT)
    private EntityIdService entityIdService;

    @Mock
    private EntityListener entityListener;

    @Mock
    private SidecarContractMigration sidecarContractMigration;

    @Mock
    private TransactionHandlerFactory transactionHandlerFactory;

    @Mock
    private TransactionHandler transactionHandler;

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
        contractResultService = new ContractResultServiceImpl(
                entityProperties, entityIdService, entityListener, sidecarContractMigration, transactionHandlerFactory);
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
}
