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
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody.Builder;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenAssociation;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.ArrayList;
import java.util.List;
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
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hiero.mirror.importer.parser.record.entity.EntityRecordItemListener;
import org.hiero.mirror.importer.parser.record.entity.MultiPartyTransferEventsGenerator;
import org.hiero.mirror.importer.parser.record.transactionhandler.TransactionHandler;
import org.hiero.mirror.importer.parser.record.transactionhandler.TransactionHandlerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

    private MultiPartyTransferEventsGenerator multiPartyTransferEventsGenerator;

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
        multiPartyTransferEventsGenerator = new MultiPartyTransferEventsGenerator(syntheticContractLogService);
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
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));
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
    @DisplayName("Should not create synthetic contract log with contract with existing parent logs")
    void doNotCreateWithContractWithParentLogs() {
        var parentRecordItem = recordItemBuilder.contractCall().build();
        parentRecordItem.getAndIncrementLogIndex();

        recordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.setParentConsensusTimestamp(Timestamp.newBuilder()
                        .setSeconds(parentRecordItem.getConsensusTimestamp() / 1_000_000_000)
                        .setNanos((int) (parentRecordItem.getConsensusTimestamp() % 1_000_000_000))))
                .recordItem(r -> r.previous(parentRecordItem))
                .build();

        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));
        verify(entityListener, times(0)).onContractLog(any());
    }

    @Test
    @DisplayName("Should create synthetic contract log with contract with existing parent logs")
    void createWithContractWithNoParentLogs() {
        var parentRecordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.clearContractCallResult())
                .build();

        recordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.setParentConsensusTimestamp(Timestamp.newBuilder()
                        .setSeconds(parentRecordItem.getConsensusTimestamp() / 1_000_000_000)
                        .setNanos((int) (parentRecordItem.getConsensusTimestamp() % 1_000_000_000))))
                .recordItem(r -> r.previous(parentRecordItem))
                .build();

        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));
        verify(entityListener, times(1)).onContractLog(any());
    }

    @Test
    @DisplayName("Should not create synthetic contract log with entity property turned off")
    void createTurnedOff() {
        entityProperties.getPersist().setSyntheticContractLogs(false);
        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));
        verify(entityListener, times(0)).onContractLog(any());
    }

    @ParameterizedTest
    @EnumSource(MultiPartyTransferType.class)
    @DisplayName("Should create synthetic contract logs for multi-party fungible token transfers from HAPI")
    void createSyntheticLogsForVariableTokenTransfersFromHapiTransactions(final MultiPartyTransferType transferType) {
        entityProperties.getPersist().setSyntheticContractLogsMulti(true);
        entityProperties.getPersist().setSyntheticContractLogs(true);

        recordItem = cryptoTransferWithMultiPartyTokenTransfers(transferType)
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
                syntheticContractResultService,
                multiPartyTransferEventsGenerator);

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

    @Test
    @DisplayName(
            "Should create equal number of synthetic contract logs for multi-party fungible token transfers with mixed "
                    + "order but matching pairs")
    void validateVariableTokenTransfersWithDifferentOrderButMatchingParisProduceEqualNumberOfEvents() {
        entityProperties.getPersist().setSyntheticContractLogsMulti(true);
        entityProperties.getPersist().setSyntheticContractLogs(true);

        recordItem = cryptoTransferWithMultiPartyTokenTransfers(
                        MultiPartyTransferType.PAIRED_SENDERS_AND_RECEIVERS_OF_TWO_PAIRS_WITH_DIFFERENT_AMOUNT)
                .recordItem(r -> r.hapiVersion(new Version(0, 72, 0)))
                .build();
        final var recordItem2 = cryptoTransferWithMultiPartyTokenTransfers(
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
                syntheticContractResultService,
                multiPartyTransferEventsGenerator);

        entityRecordItemListener.onItem(recordItem);

        var contractLogCaptor = ArgumentCaptor.forClass(ContractLog.class);
        verify(entityListener, atLeast(1)).onContractLog(contractLogCaptor.capture());
        final var syntheticLogs = contractLogCaptor.getAllValues().stream()
                .filter(ContractLog::isSyntheticTransfer)
                .toList();

        // Reset the mock to clear previous invocations before the second call
        reset(entityListener);

        entityRecordItemListener.onItem(recordItem2);

        var contractLogCaptor2 = ArgumentCaptor.forClass(ContractLog.class);
        verify(entityListener, atLeast(1)).onContractLog(contractLogCaptor2.capture());
        final var syntheticLogs2 = contractLogCaptor2.getAllValues().stream()
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
            case ONE_RECEIVER_TWO_SENDERS,
                    PAIRED_SENDERS_AND_RECEIVERS_OF_TWO_PAIRS_WITH_DIFFERENT_AMOUNT,
                    PAIRED_SENDERS_AND_RECEIVERS_OF_TWO_PAIRS_WITH_DIFFERENT_AMOUNT_MIXED_ORDER,
                    PAIRED_SENDERS_AND_RECEIVERS_OF_TWO_PAIRS_WITH_THE_SAME_AMOUNT -> 2;
            case PAIRED_SENDERS_AND_RECEIVERS_OF_THREE_PAIRS -> 3;
            case ONE_RECEIVER_FOUR_SENDERS, THREE_RECEIVERS_INCLUDING_ZERO_SENT_AMOUNT -> 4;
            case THREE_RECEIVERS_WITH_DIFFERENT_AMOUNT,
                    THREE_RECEIVERS_WITH_THE_SAME_AMOUNT,
                    THREE_RECEIVERS_WITH_DIFFERENT_AMOUNT_DO_NOT_ZERO_SUM -> 6;
            case FOUR_RECEIVERS_WITH_DIFFERENT_AMOUNT -> 7;
        };
    }

    private record LogEntry(EntityId senderId, EntityId receiverId, long amount) {}

    /**
     * Creates a crypto transfer with multi-party token transfers based on transfer type.
     *
     * @param transferType the multi-party transfer type
     * @return the builder
     */
    private RecordItemBuilder.Builder<Builder> cryptoTransferWithMultiPartyTokenTransfers(
            final MultiPartyTransferType transferType) {
        var builder = recordItemBuilder.cryptoTransfer();
        var tokenId = recordItemBuilder.tokenId();
        var tokenTransfers = TokenTransferList.newBuilder().setToken(tokenId);

        var transferCount = getTransferCount(transferType);

        var accounts = new ArrayList<AccountID>();
        for (int i = 0; i < transferCount; i++) {
            var account = recordItemBuilder.accountId();
            accounts.add(account);
            recordItemBuilder.updateState(TokenAssociation.newBuilder()
                    .setAccountId(account)
                    .setTokenId(tokenId)
                    .build());
        }

        populateTokenTransfersBasedOnType(transferType, tokenTransfers, accounts);

        builder.record(r -> r.addTokenTransferLists(tokenTransfers));

        return builder;
    }

    /**
     * Populate the {@link TokenTransferList} with the necessary {@link AccountAmount} for senders and receivers
     * based on the transfer type
     * */
    private void populateTokenTransfersBasedOnType(
            final MultiPartyTransferType transferType,
            final TokenTransferList.Builder tokenTransfers,
            final List<AccountID> accounts) {
        switch (transferType) {
            case ONE_RECEIVER_TWO_SENDERS:
                // [A=1000, B=-400, C=-600] => [[C=-600, A=600], [B=-400, A=400]]
                tokenTransfers
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(0), 1000))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(1), -400))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(2), -600));
                break;
            case PAIRED_SENDERS_AND_RECEIVERS_OF_TWO_PAIRS_WITH_DIFFERENT_AMOUNT:
                // [A=400, B=-400, C=300, D=-300] => [[B=-400, A=400], [D=-300, C=300]]
                tokenTransfers
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(0), 400))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(1), -400))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(2), 300))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(3), -300));
                break;
            case PAIRED_SENDERS_AND_RECEIVERS_OF_TWO_PAIRS_WITH_DIFFERENT_AMOUNT_MIXED_ORDER:
                // [A=400, B=-300, C=300, D=-400] => [[D=-400, A=400], [B=-300, C=300]]
                tokenTransfers
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(0), 400))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(1), -300))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(2), 300))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(3), -400));
                break;
            case PAIRED_SENDERS_AND_RECEIVERS_OF_TWO_PAIRS_WITH_THE_SAME_AMOUNT:
                // [A=400, B=-400, C=400, D=-400] => [[B=-400, A=400], [D=-400, C=400]]
                tokenTransfers
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(0), 400))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(1), -400))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(2), 400))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(3), -400));
                break;
            case ONE_RECEIVER_FOUR_SENDERS:
                // [A=1500, B=-500, C=-400, D=-300, E=-300] => [[B=-500, A=500], [C=-400, A=400], [D=-300, A=300],
                // [E=-300, A=300]]
                tokenTransfers
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(0), 1500))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(1), -500))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(2), -400))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(3), -300))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(4), -300));
                break;
            case PAIRED_SENDERS_AND_RECEIVERS_OF_THREE_PAIRS:
                // [A=400, B=-400, C=300, D=-300, E=200, F=-200] =>  [[B=-400, A=400], [D=-300, C=300], [F=-200, E=200]]
                tokenTransfers
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(0), 400))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(1), -400))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(2), 300))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(3), -300))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(4), 200))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(5), -200));
                break;
            case THREE_RECEIVERS_WITH_DIFFERENT_AMOUNT:
                // [A=1000, B=-400, C=-600, D=500, E=400, F=-800, G=-100] => [[F=-800, A=800], [C=-200, A=200],
                // [C=-400, D=400], [B=-100, D=100], [B=-300, E=300], [G=-100, E=100]]
                tokenTransfers
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(0), 1000))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(1), -400))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(2), -600))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(3), 500))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(4), 400))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(5), -800))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(6), -100));
                break;
            case FOUR_RECEIVERS_WITH_DIFFERENT_AMOUNT:
                // [A=900, B=-300, C=-400, D=600, E=400, F=-800, G=-450, H=50] => [[F=-800, A=800], [G=-100, A=100],
                // [G=-350, D=350], [C=-250, D=250], [C=-150, E=150], [B=-250, E=250], [B=-50, H=50]]
                tokenTransfers
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(0), 900))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(1), -300))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(2), -400))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(3), 600))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(4), 400))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(5), -800))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(6), -450))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(7), 50));
                break;
            case THREE_RECEIVERS_WITH_THE_SAME_AMOUNT:
                // [A=1000, B=-1400, C=1000, D=-100, E=-900, F=-600, G=1000] => [[B=-1000, A=1000],  [F=-600, G=600],
                // [E=-500, C=500], [B=-400, C=400], [E=-400, G=400], [D=-100, C=100]]
                tokenTransfers
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(0), 1000))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(1), -1400))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(2), 1000))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(3), -100))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(4), -900))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(5), -600))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(6), 1000));
                break;
            case THREE_RECEIVERS_WITH_DIFFERENT_AMOUNT_DO_NOT_ZERO_SUM:
                // [A=1000, B=-400, C=-600, D=500, E=400, F=-800, G=-101] => [[F=-800, A=800], [C=-200, A=200],
                // [C=-400, D=400], [B=-100, D=100], [B=-300, E=300], [G=-100, E=100]]
                tokenTransfers
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(0), 1000))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(1), -400))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(2), -600))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(3), 500))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(4), 400))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(5), -800))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(6), -101));
                break;
            case THREE_RECEIVERS_INCLUDING_ZERO_SENT_AMOUNT:
                // [A=1000, B=-400, C=-600, D=500, E=400, F=0, G=-900, H=0] => [[G=-900, A=900], [C=-100, A=100],
                // [C=-500, D=500], [B=-400, E=400]]
                tokenTransfers
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(0), 1000))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(1), -400))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(2), -600))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(3), 500))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(4), 400))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(5), 0))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(6), -900))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(7), 0));
                break;
            default:
                throw new IllegalArgumentException("Unsupported transfer type: " + transferType);
        }
    }

    /**
     * Returns the number of transfers (accounts) needed for the given transfer type.
     *
     * @param transferType the multi-party transfer type
     * @return the number of transfers
     */
    private int getTransferCount(final MultiPartyTransferType transferType) {
        return switch (transferType) {
            case ONE_RECEIVER_TWO_SENDERS -> 3;
            case PAIRED_SENDERS_AND_RECEIVERS_OF_TWO_PAIRS_WITH_DIFFERENT_AMOUNT -> 4;
            case PAIRED_SENDERS_AND_RECEIVERS_OF_TWO_PAIRS_WITH_DIFFERENT_AMOUNT_MIXED_ORDER -> 4;
            case PAIRED_SENDERS_AND_RECEIVERS_OF_TWO_PAIRS_WITH_THE_SAME_AMOUNT -> 4;
            case ONE_RECEIVER_FOUR_SENDERS -> 5;
            case PAIRED_SENDERS_AND_RECEIVERS_OF_THREE_PAIRS -> 6;
            case THREE_RECEIVERS_WITH_DIFFERENT_AMOUNT -> 7;
            case FOUR_RECEIVERS_WITH_DIFFERENT_AMOUNT -> 8;
            case THREE_RECEIVERS_WITH_THE_SAME_AMOUNT -> 7;
            case THREE_RECEIVERS_WITH_DIFFERENT_AMOUNT_DO_NOT_ZERO_SUM -> 7;
            case THREE_RECEIVERS_INCLUDING_ZERO_SENT_AMOUNT -> 8;
        };
    }

    private enum MultiPartyTransferType {
        ONE_RECEIVER_TWO_SENDERS,
        ONE_RECEIVER_FOUR_SENDERS,
        PAIRED_SENDERS_AND_RECEIVERS_OF_TWO_PAIRS_WITH_DIFFERENT_AMOUNT,
        PAIRED_SENDERS_AND_RECEIVERS_OF_TWO_PAIRS_WITH_DIFFERENT_AMOUNT_MIXED_ORDER,
        PAIRED_SENDERS_AND_RECEIVERS_OF_TWO_PAIRS_WITH_THE_SAME_AMOUNT,
        PAIRED_SENDERS_AND_RECEIVERS_OF_THREE_PAIRS,
        THREE_RECEIVERS_WITH_DIFFERENT_AMOUNT,
        FOUR_RECEIVERS_WITH_DIFFERENT_AMOUNT,
        THREE_RECEIVERS_WITH_THE_SAME_AMOUNT,
        THREE_RECEIVERS_WITH_DIFFERENT_AMOUNT_DO_NOT_ZERO_SUM,
        THREE_RECEIVERS_INCLUDING_ZERO_SENT_AMOUNT
    }
}
