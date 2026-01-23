// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.fromTrimmedEvmAddress;
import static org.hiero.mirror.common.util.DomainUtils.trim;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.ArrayList;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.importer.domain.ContractResultService;
import org.hiero.mirror.importer.domain.EntityIdService;
import org.hiero.mirror.importer.parser.CommonParserProperties;
import org.hiero.mirror.importer.parser.contractresult.SyntheticContractResultService;
import org.hiero.mirror.importer.parser.domain.RecordItemBuilder;
import org.hiero.mirror.importer.parser.domain.RecordItemBuilder.MultiPartyTransferType;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hiero.mirror.importer.parser.record.entity.EntityRecordItemListener;
import org.hiero.mirror.importer.parser.record.transactionhandler.TransactionHandler;
import org.hiero.mirror.importer.parser.record.transactionhandler.TransactionHandlerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.util.Version;

@ExtendWith(MockitoExtension.class)
class SyntheticContractLogServiceImplTest {
    private final RecordItemBuilder recordItemBuilder = new RecordItemBuilder();
    private final EntityProperties entityProperties = new EntityProperties(new SystemEntity(new CommonProperties()));

    @Mock
    private EntityListener entityListener;

    @Mock
    private ContractResultService contractResultService;

    @Mock
    private EntityIdService entityIdService;

    @Mock
    private TransactionHandlerFactory transactionHandlerFactory;

    @Mock
    private TransactionHandler transactionHandler;

    @Mock
    private SyntheticContractResultService syntheticContractResultService;

    private CommonParserProperties commonParserProperties;

    private SyntheticContractLogService syntheticContractLogService;

    private RecordItem recordItem;
    private EntityId entityTokenId;
    private EntityId senderId;
    private EntityId receiverId;
    private long amount;

    @BeforeEach
    void beforeEach() {
        commonParserProperties = new CommonParserProperties();
        syntheticContractLogService = new SyntheticContractLogServiceImpl(entityListener, entityProperties);
        recordItem = recordItemBuilder.tokenMint(TokenType.FUNGIBLE_COMMON).build();

        TokenID tokenId = recordItem.getTransactionBody().getTokenMint().getToken();
        entityTokenId = EntityId.of(tokenId);
        senderId = EntityId.EMPTY;
        receiverId =
                EntityId.of(recordItem.getTransactionBody().getTransactionID().getAccountID());
        amount = recordItem.getTransactionBody().getTokenMint().getAmount();
    }

    @Test
    @DisplayName("Should be able to create valid synthetic contract log")
    void createValid() {
        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount, false));
        verify(entityListener, times(1)).onContractLog(any());
    }

    @Test
    @DisplayName("Should be able to create valid synthetic contract log with indexed value")
    void createValidIndexed() {
        syntheticContractLogService.create(
                new TransferIndexedContractLog(recordItem, entityTokenId, senderId, receiverId, amount));
        verify(entityListener, times(1)).onContractLog(any());
    }

    @Test
    @DisplayName("Should not create synthetic contract log with contract")
    void createWithContract() {
        recordItem = recordItemBuilder.contractCall().build();
        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount, false));
        verify(entityListener, times(0)).onContractLog(any());
    }

    @Test
    @DisplayName("Should not create synthetic contract log with entity property turned off")
    void createTurnedOff() {
        entityProperties.getPersist().setSyntheticContractLogs(false);
        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount, false));
        verify(entityListener, times(0)).onContractLog(any());
    }

    @ParameterizedTest
    @EnumSource(MultiPartyTransferType.class)
    @DisplayName("Should create synthetic contract logs for multi-party fungible token transfers from HAPI")
    void createSyntheticLogsForVariableTokenTransfersFromHapiTransactions(final MultiPartyTransferType transferType) {
        entityProperties.getPersist().setSyntheticContractLogsMulti(true);
        entityProperties.getPersist().setSyntheticContractLogs(true);

        recordItem = recordItemBuilder
                .cryptoTransferWithMultiPartyTokenTransfers(transferType)
                // Setting hapi version after disableSyntheticEventsForMultiPartyTransfersVersion property
                .recordItem(r -> r.hapiVersion(new Version(0, 73, 0)))
                .build();

        when(transactionHandler.getEntity(any(RecordItem.class))).thenReturn(EntityId.EMPTY);
        when(transactionHandlerFactory.get(any())).thenReturn(transactionHandler);

        var entityRecordItemListener = new EntityRecordItemListener(
                commonParserProperties,
                contractResultService,
                entityIdService,
                entityListener,
                entityProperties,
                transactionHandlerFactory,
                syntheticContractLogService,
                syntheticContractResultService);

        entityRecordItemListener.onItem(recordItem);

        var contractLogCaptor = ArgumentCaptor.forClass(ContractLog.class);
        verify(entityListener, atLeast(1)).onContractLog(contractLogCaptor.capture());

        var syntheticLogs = contractLogCaptor.getAllValues().stream()
                .filter(ContractLog::isSyntheticTransfer)
                .toList();

        var tokenTransferList = recordItem.getTransactionRecord().getTokenTransferListsList().stream()
                .findFirst()
                .orElseThrow();

        var expectedLogCount = getExpectedLogCount(transferType);

        assertThat(syntheticLogs)
                .as("Should create %d synthetic logs for %s", expectedLogCount, transferType)
                .hasSize(expectedLogCount);

        var logEntries = new ArrayList<LogEntry>();
        for (var log : syntheticLogs) {
            var senderIdFromLog = fromTrimmedEvmAddress(log.getTopic1());
            var receiverIdFromLog = fromTrimmedEvmAddress(log.getTopic2());
            var dataBytes = log.getData();
            var amountFromLog = dataBytes != null && dataBytes.length > 0
                    ? Bytes.wrap(trim(dataBytes)).toLong()
                    : 0L;
            logEntries.add(new LogEntry(senderIdFromLog, receiverIdFromLog, amountFromLog));
        }

        var originalTransferSum = tokenTransferList.getTransfersList().stream()
                .mapToLong(AccountAmount::getAmount)
                .sum();

        if (transferType != MultiPartyTransferType.THREE_RECEIVERS_WITH_DIFFERENT_AMOUNT_DO_NOT_ZERO_SUM) {
            assertThat(originalTransferSum)
                    .as("Original transfers should zero-sum")
                    .isZero();
        }

        var syntheticLogSum = logEntries.stream().mapToLong(e -> e.amount).sum();
        var positiveOriginalSum = tokenTransferList.getTransfersList().stream()
                .mapToLong(AccountAmount::getAmount)
                .filter(a -> a > 0)
                .sum();
        assertThat(syntheticLogSum)
                .as("Sum of synthetic log amounts should equal sum of positive original amounts")
                .isEqualTo(positiveOriginalSum);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Should create synthetic contract logs for multi-party fungible token transfers from contract calls")
    void createSyntheticLogsForVariableTokenTransfersFromContractCallTransactions(
            boolean beforeConsensusNodeSyntheticEventsSupport) {
        entityProperties.getPersist().setSyntheticContractLogsMulti(true);
        entityProperties.getPersist().setSyntheticContractLogs(true);

        recordItem = recordItemBuilder
                .cryptoTransferWithMultiPartyTokenTransfers(MultiPartyTransferType.ONE_RECEIVER_FOUR_SENDERS)
                .recordItem(r -> r.hapiVersion(
                        beforeConsensusNodeSyntheticEventsSupport ? new Version(0, 60, 0) : new Version(0, 73, 0)))
                .record(r -> r.setContractCallResult(
                        ContractFunctionResult.newBuilder().build()))
                .build();

        when(transactionHandler.getEntity(any(RecordItem.class))).thenReturn(EntityId.EMPTY);
        when(transactionHandlerFactory.get(any())).thenReturn(transactionHandler);

        var entityRecordItemListener = new EntityRecordItemListener(
                commonParserProperties,
                contractResultService,
                entityIdService,
                entityListener,
                entityProperties,
                transactionHandlerFactory,
                syntheticContractLogService,
                syntheticContractResultService);

        entityRecordItemListener.onItem(recordItem);

        var contractLogCaptor = ArgumentCaptor.forClass(ContractLog.class);
        if (beforeConsensusNodeSyntheticEventsSupport) {
            verify(entityListener, atLeast(1)).onContractLog(contractLogCaptor.capture());
            var syntheticLogs = contractLogCaptor.getAllValues().stream()
                    .filter(ContractLog::isSyntheticTransfer)
                    .toList();

            var tokenTransferList = recordItem.getTransactionRecord().getTokenTransferListsList().stream()
                    .findFirst()
                    .orElseThrow();

            var expectedLogCount = getExpectedLogCount(MultiPartyTransferType.ONE_RECEIVER_FOUR_SENDERS);

            assertThat(syntheticLogs)
                    .as(
                            "Should create %d synthetic logs for %s",
                            expectedLogCount, MultiPartyTransferType.ONE_RECEIVER_FOUR_SENDERS)
                    .hasSize(expectedLogCount);

            var logEntries = new ArrayList<LogEntry>();
            for (var log : syntheticLogs) {
                var senderIdFromLog = fromTrimmedEvmAddress(log.getTopic1());
                var receiverIdFromLog = fromTrimmedEvmAddress(log.getTopic2());
                var dataBytes = log.getData();
                var amountFromLog = dataBytes != null && dataBytes.length > 0
                        ? Bytes.wrap(trim(dataBytes)).toLong()
                        : 0L;
                logEntries.add(new LogEntry(senderIdFromLog, receiverIdFromLog, amountFromLog));
            }

            var originalTransferSum = tokenTransferList.getTransfersList().stream()
                    .mapToLong(AccountAmount::getAmount)
                    .sum();
            assertThat(originalTransferSum)
                    .as("Original transfers should zero-sum")
                    .isZero();

            var syntheticLogSum = logEntries.stream().mapToLong(e -> e.amount).sum();
            var positiveOriginalSum = tokenTransferList.getTransfersList().stream()
                    .mapToLong(AccountAmount::getAmount)
                    .filter(a -> a > 0)
                    .sum();
            assertThat(syntheticLogSum)
                    .as("Sum of synthetic log amounts should equal sum of positive original amounts")
                    .isEqualTo(positiveOriginalSum);
        } else {
            verify(entityListener, atLeast(0)).onContractLog(contractLogCaptor.capture());
            var syntheticLogs = contractLogCaptor.getAllValues().stream()
                    .filter(ContractLog::isSyntheticTransfer)
                    .toList();
            assertThat(syntheticLogs).isEmpty();
        }
    }

    @Test
    @DisplayName(
            "Should create equal number of synthetic contract logs for multi-party fungible token transfers with mixed order but matching pairs")
    void validateVariableTokenTransfersWithDifferentOrderButMatchingParisProduceEqualNumberOfEvents() {
        entityProperties.getPersist().setSyntheticContractLogsMulti(true);
        entityProperties.getPersist().setSyntheticContractLogs(true);

        recordItem = recordItemBuilder
                .cryptoTransferWithMultiPartyTokenTransfers(
                        MultiPartyTransferType.PAIRED_SENDERS_AND_RECEIVERS_OF_TWO_PAIRS_WITH_DIFFERENT_AMOUNT)
                .recordItem(r -> r.hapiVersion(new Version(0, 72, 0)))
                .build();
        final var recordItem2 = recordItemBuilder
                .cryptoTransferWithMultiPartyTokenTransfers(
                        MultiPartyTransferType
                                .PAIRED_SENDERS_AND_RECEIVERS_OF_TWO_PAIRS_WITH_DIFFERENT_AMOUNT_MIXED_ORDER)
                .recordItem(r -> r.hapiVersion(new Version(0, 72, 0)))
                .build();

        when(transactionHandler.getEntity(any(RecordItem.class))).thenReturn(EntityId.EMPTY);
        when(transactionHandlerFactory.get(any())).thenReturn(transactionHandler);

        var entityRecordItemListener = new EntityRecordItemListener(
                commonParserProperties,
                contractResultService,
                entityIdService,
                entityListener,
                entityProperties,
                transactionHandlerFactory,
                syntheticContractLogService,
                syntheticContractResultService);

        entityRecordItemListener.onItem(recordItem);

        var contractLogCaptor = ArgumentCaptor.forClass(ContractLog.class);
        verify(entityListener, atLeast(1)).onContractLog(contractLogCaptor.capture());
        var syntheticLogs = contractLogCaptor.getAllValues().stream()
                .filter(ContractLog::isSyntheticTransfer)
                .toList();

        // Reset the mock to clear previous invocations before the second call
        reset(entityListener);

        entityRecordItemListener.onItem(recordItem2);

        var contractLogCaptor2 = ArgumentCaptor.forClass(ContractLog.class);
        verify(entityListener, atLeast(1)).onContractLog(contractLogCaptor2.capture());
        var syntheticLogs2 = contractLogCaptor2.getAllValues().stream()
                .filter(ContractLog::isSyntheticTransfer)
                .toList();

        assertThat(syntheticLogs).hasSameSizeAs(syntheticLogs2);
    }

    /**
     * Returns the expected number of synthetic logs for the given transfer type.
     *
     * @param transferType the multi-party transfer type
     * @return the expected number of synthetic logs
     */
    private int getExpectedLogCount(MultiPartyTransferType transferType) {
        return switch (transferType) {
            case ONE_RECEIVER_TWO_SENDERS -> 2;
            case PAIRED_SENDERS_AND_RECEIVERS_OF_TWO_PAIRS_WITH_DIFFERENT_AMOUNT -> 2;
            case PAIRED_SENDERS_AND_RECEIVERS_OF_TWO_PAIRS_WITH_DIFFERENT_AMOUNT_MIXED_ORDER -> 2;
            case PAIRED_SENDERS_AND_RECEIVERS_OF_TWO_PAIRS_WITH_THE_SAME_AMOUNT -> 2;
            case ONE_RECEIVER_FOUR_SENDERS -> 4;
            case PAIRED_SENDERS_AND_RECEIVERS_OF_THREE_PAIRS -> 3;
            case TWO_RECEIVERS_WITH_DIFFERENT_AMOUNT -> 6;
            case FOUR_RECEIVERS_WITH_DIFFERENT_AMOUNT -> 7;
            case THREE_RECEIVERS_WITH_THE_SAME_AMOUNT -> 6;
            case THREE_RECEIVERS_WITH_DIFFERENT_AMOUNT_DO_NOT_ZERO_SUM -> 6;
            case THREE_RECEIVERS_INCLUDING_ZERO_SENT_AMOUNT -> 4;
        };
    }

    private record LogEntry(EntityId senderId, EntityId receiverId, long amount) {}
}
