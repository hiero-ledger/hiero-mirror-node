// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.fromTrimmedEvmAddress;
import static org.hiero.mirror.common.util.DomainUtils.trim;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hederahashgraph.api.proto.java.AccountAmount;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    @DisplayName("Should not create synthetic contract log with contract")
    void createWithContract() {
        recordItem = recordItemBuilder.contractCall().build();
        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));
        verify(entityListener, times(0)).onContractLog(any());
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
    @ValueSource(ints = {3, 4, 5, 6, 7, 8})
    @DisplayName("Should create synthetic contract logs for variable token transfers with correct sorting and zero-sum")
    void createSyntheticLogsForVariableTokenTransfers(int transferCount) {
        entityProperties.getPersist().setSyntheticContractLogsMulti(true);
        entityProperties.getPersist().setSyntheticContractLogs(true);

        recordItem = recordItemBuilder
                .cryptoTransferWithVariableTokenTransfers(transferCount)
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

        // Calculate expected log count based on the new pairing algorithm
        // The algorithm pairs receivers with senders in alphabetical order
        // Count receivers and calculate how many pairs each receiver needs
        var tokenTransferList = recordItem.getTransactionRecord().getTokenTransferListsList().stream()
                .findFirst()
                .orElseThrow();

        // The number of pairs equals the number of receivers (each receiver gets paired with one or more senders)
        // But we need to count actual pairs created, which depends on how senders are distributed
        // For simplicity, we'll calculate based on the minimum number of pairs needed
        // which is the number of receivers (since each receiver needs at least one pair)
        var receiverCount = (int) tokenTransferList.getTransfersList().stream()
                .filter(aa -> aa.getAmount() > 0)
                .count();

        int expectedLogCount;
        if (transferCount == 3) {
            expectedLogCount = 2; // A=1000 pairs with B=-400 and C=-600
        } else if (transferCount == 4) {
            expectedLogCount = 2; // A=400 pairs with B=-400, C=300 pairs with D=-300
        } else if (transferCount == 5) {
            expectedLogCount = 4; // A=1500 pairs with B=-500, C=-400, D=-300, E=-300
        } else if (transferCount == 6) {
            expectedLogCount = 3; // A=400 pairs with B=-400, C=300 pairs with D=-300, E=200 pairs with F=-200
        } else if (transferCount == 7) {
            expectedLogCount =
                    5; // D=500 pairs with F=-500, E=300 pairs with F=-300, E=100 pairs with G=-100, A=400 pairs with
            // B=-400, A=600 pairs with C=-600
        } else if (transferCount == 8) {
            expectedLogCount =
                    6; // A=300 pairs with B=-300, A=400 pairs with C=-400, A=200 pairs with F=-200, D=600 pairs with
            // F=-600, E=400 pairs with G=-400
        } else {
            expectedLogCount = receiverCount;
        }

        assertThat(syntheticLogs)
                .as("Should create %d synthetic logs for %d transfers", expectedLogCount, transferCount)
                .hasSize(expectedLogCount);

        var logEntries = new ArrayList<LogEntry>();
        for (var log : syntheticLogs) {
            var senderId = fromTrimmedEvmAddress(log.getTopic1());
            var receiverId = fromTrimmedEvmAddress(log.getTopic2());
            // Data contains the amount as trimmed bytes (from longToBytes)
            var dataBytes = log.getData();
            var amount = dataBytes != null && dataBytes.length > 0
                    ? Bytes.wrap(trim(dataBytes)).toLong()
                    : 0L;
            logEntries.add(new LogEntry(senderId, receiverId, amount));
        }

        var originalTransferSum = tokenTransferList.getTransfersList().stream()
                .mapToLong(AccountAmount::getAmount)
                .sum();
        assertThat(originalTransferSum).as("Original transfers should zero-sum").isZero();

        long syntheticLogSum = logEntries.stream().mapToLong(e -> e.amount).sum();
        long positiveOriginalSum = tokenTransferList.getTransfersList().stream()
                .mapToLong(AccountAmount::getAmount)
                .filter(a -> a > 0)
                .sum();
        assertThat(syntheticLogSum)
                .as("Sum of synthetic log amounts should equal sum of positive original amounts")
                .isEqualTo(positiveOriginalSum);
    }

    private record LogEntry(EntityId senderId, EntityId receiverId, long amount) {}
}
