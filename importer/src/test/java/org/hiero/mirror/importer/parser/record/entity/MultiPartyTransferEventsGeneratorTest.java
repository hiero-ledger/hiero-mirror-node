// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.fromTrimmedEvmAddress;
import static org.hiero.mirror.common.util.DomainUtils.trim;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.importer.parser.contractlog.SyntheticContractLogService;
import org.hiero.mirror.importer.parser.contractlog.TransferContractLog;
import org.hiero.mirror.importer.parser.domain.RecordItemBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MultiPartyTransferEventsGeneratorTest {

    private final RecordItemBuilder recordItemBuilder = new RecordItemBuilder();

    @Mock
    private SyntheticContractLogService syntheticContractLogService;

    private MultiPartyTransferEventsGenerator multiPartyTransferEventsGenerator;

    private RecordItem recordItem;

    @BeforeEach
    void beforeEach() {
        multiPartyTransferEventsGenerator = new MultiPartyTransferEventsGenerator(syntheticContractLogService);
    }

    @ParameterizedTest
    @EnumSource(MultiPartyTransferType.class)
    @DisplayName("Should create synthetic contract logs for multi-party fungible token transfers")
    void createSyntheticLogsForMultiPartyTokenTransfers(final MultiPartyTransferType transferType) {
        var tokenId = recordItemBuilder.tokenId();
        var tokenTransfers = TokenTransferList.newBuilder().setToken(tokenId);
        var transferCount = getTransferCount(transferType);

        var accounts = new ArrayList<AccountID>();
        for (int i = 0; i < transferCount; i++) {
            accounts.add(recordItemBuilder.accountId());
        }

        populateTokenTransfersBasedOnType(transferType, tokenTransfers, accounts);

        var entityTokenId = EntityId.of(tokenId);
        var tokenTransferList = tokenTransfers.build();

        multiPartyTransferEventsGenerator.generate(recordItem, entityTokenId, tokenTransferList.getTransfersList());

        var contractLogCaptor = ArgumentCaptor.forClass(TransferContractLog.class);
        verify(syntheticContractLogService, atLeast(1)).create(contractLogCaptor.capture());

        var syntheticLogs = contractLogCaptor.getAllValues();
        var expectedLogCount = getExpectedLogCount(transferType);

        assertThat(syntheticLogs)
                .as("Should create %d synthetic logs for %s", expectedLogCount, transferType)
                .hasSize(expectedLogCount);

        var originalTransferSum = tokenTransferList.getTransfersList().stream()
                .mapToLong(AccountAmount::getAmount)
                .sum();

        if (transferType != MultiPartyTransferType.THREE_RECEIVERS_WITH_DIFFERENT_AMOUNT_DO_NOT_ZERO_SUM) {
            assertThat(originalTransferSum)
                    .as("Original transfers should zero-sum")
                    .isZero();
        }

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
    @EnumSource(
            value = MultiPartyTransferType.class,
            names = {
                "PAIRED_SENDERS_AND_RECEIVERS_OF_TWO_PAIRS_WITH_DIFFERENT_AMOUNT",
                "PAIRED_SENDERS_AND_RECEIVERS_OF_TWO_PAIRS_WITH_DIFFERENT_AMOUNT_MIXED_ORDER"
            })
    @DisplayName(
            "Should create equal number of synthetic contract logs for transfers with different order but matching pairs")
    void validateTransfersWithDifferentOrderButMatchingPairsProduceEqualNumberOfEvents(
            final MultiPartyTransferType transferType) {
        var tokenId = recordItemBuilder.tokenId();
        var entityTokenId = EntityId.of(tokenId);

        // First transfer
        var tokenTransfers1 = TokenTransferList.newBuilder().setToken(tokenId);
        var accounts1 = new ArrayList<AccountID>();
        for (int i = 0; i < getTransferCount(transferType); i++) {
            accounts1.add(recordItemBuilder.accountId());
        }
        populateTokenTransfersBasedOnType(
                MultiPartyTransferType.PAIRED_SENDERS_AND_RECEIVERS_OF_TWO_PAIRS_WITH_DIFFERENT_AMOUNT,
                tokenTransfers1,
                accounts1);

        multiPartyTransferEventsGenerator.generate(
                recordItem, entityTokenId, tokenTransfers1.build().getTransfersList());

        var captor1 = ArgumentCaptor.forClass(TransferContractLog.class);
        verify(syntheticContractLogService, atLeast(1)).create(captor1.capture());
        var syntheticLogs1 = captor1.getAllValues();

        reset(syntheticContractLogService);

        // Second transfer with different order
        var tokenTransfers2 = TokenTransferList.newBuilder().setToken(tokenId);
        var accounts2 = new ArrayList<AccountID>();
        for (int i = 0; i < getTransferCount(transferType); i++) {
            accounts2.add(recordItemBuilder.accountId());
        }
        populateTokenTransfersBasedOnType(
                MultiPartyTransferType.PAIRED_SENDERS_AND_RECEIVERS_OF_TWO_PAIRS_WITH_DIFFERENT_AMOUNT_MIXED_ORDER,
                tokenTransfers2,
                accounts2);

        multiPartyTransferEventsGenerator.generate(
                recordItem, entityTokenId, tokenTransfers2.build().getTransfersList());

        var captor2 = ArgumentCaptor.forClass(TransferContractLog.class);
        verify(syntheticContractLogService, atLeast(1)).create(captor2.capture());
        var syntheticLogs2 = captor2.getAllValues();

        assertThat(syntheticLogs1).hasSameSizeAs(syntheticLogs2);
    }

    @Test
    @DisplayName(
            "Should create equal number of synthetic contract logs for multi-party fungible token transfers with mixed "
                    + "order but matching pairs")
    void validateVariableTokenTransfersWithDifferentOrderButMatchingParisProduceEqualNumberOfEvents() {
        var tokenId = recordItemBuilder.tokenId();
        var entityTokenId = EntityId.of(tokenId);

        // First transfer
        var tokenTransfers1 = TokenTransferList.newBuilder().setToken(tokenId);
        var accounts1 = new ArrayList<AccountID>();
        for (int i = 0;
                i
                        < getTransferCount(
                                MultiPartyTransferType.PAIRED_SENDERS_AND_RECEIVERS_OF_TWO_PAIRS_WITH_DIFFERENT_AMOUNT);
                i++) {
            accounts1.add(recordItemBuilder.accountId());
        }
        populateTokenTransfersBasedOnType(
                MultiPartyTransferType.PAIRED_SENDERS_AND_RECEIVERS_OF_TWO_PAIRS_WITH_DIFFERENT_AMOUNT,
                tokenTransfers1,
                accounts1);

        multiPartyTransferEventsGenerator.generate(
                recordItem, entityTokenId, tokenTransfers1.build().getTransfersList());

        var captor1 = ArgumentCaptor.forClass(TransferContractLog.class);
        verify(syntheticContractLogService, atLeast(1)).create(captor1.capture());
        var syntheticLogs1 = captor1.getAllValues();

        reset(syntheticContractLogService);

        // Second transfer with a different order
        var tokenTransfers2 = TokenTransferList.newBuilder().setToken(tokenId);
        var accounts2 = new ArrayList<AccountID>();
        for (int i = 0;
                i
                        < getTransferCount(
                                MultiPartyTransferType
                                        .PAIRED_SENDERS_AND_RECEIVERS_OF_TWO_PAIRS_WITH_DIFFERENT_AMOUNT_MIXED_ORDER);
                i++) {
            accounts2.add(recordItemBuilder.accountId());
        }
        populateTokenTransfersBasedOnType(
                MultiPartyTransferType.PAIRED_SENDERS_AND_RECEIVERS_OF_TWO_PAIRS_WITH_DIFFERENT_AMOUNT_MIXED_ORDER,
                tokenTransfers2,
                accounts2);

        multiPartyTransferEventsGenerator.generate(
                recordItem, entityTokenId, tokenTransfers2.build().getTransfersList());

        var captor2 = ArgumentCaptor.forClass(TransferContractLog.class);
        verify(syntheticContractLogService, atLeast(1)).create(captor2.capture());
        var syntheticLogs2 = captor2.getAllValues();

        assertThat(syntheticLogs1).hasSameSizeAs(syntheticLogs2);
    }

    /**
     * Returns the expected number of synthetic logs for the given transfer type.
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

    /**
     * Populate the TokenTransferList with the necessary AccountAmount for senders and receivers
     * based on the transfer type.
     */
    private void populateTokenTransfersBasedOnType(
            final MultiPartyTransferType transferType,
            final TokenTransferList.Builder tokenTransfers,
            final List<AccountID> accounts) {
        switch (transferType) {
            case ONE_RECEIVER_TWO_SENDERS:
                tokenTransfers
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(0), 1000))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(1), -400))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(2), -600));
                break;
            case PAIRED_SENDERS_AND_RECEIVERS_OF_TWO_PAIRS_WITH_DIFFERENT_AMOUNT:
                tokenTransfers
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(0), 400))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(1), -400))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(2), 300))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(3), -300));
                break;
            case PAIRED_SENDERS_AND_RECEIVERS_OF_TWO_PAIRS_WITH_DIFFERENT_AMOUNT_MIXED_ORDER:
                tokenTransfers
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(0), 400))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(1), -300))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(2), 300))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(3), -400));
                break;
            case PAIRED_SENDERS_AND_RECEIVERS_OF_TWO_PAIRS_WITH_THE_SAME_AMOUNT:
                tokenTransfers
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(0), 400))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(1), -400))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(2), 400))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(3), -400));
                break;
            case ONE_RECEIVER_FOUR_SENDERS:
                tokenTransfers
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(0), 1500))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(1), -500))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(2), -400))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(3), -300))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(4), -300));
                break;
            case PAIRED_SENDERS_AND_RECEIVERS_OF_THREE_PAIRS:
                tokenTransfers
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(0), 400))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(1), -400))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(2), 300))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(3), -300))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(4), 200))
                        .addTransfers(recordItemBuilder.accountAmount(accounts.get(5), -200));
                break;
            case THREE_RECEIVERS_WITH_DIFFERENT_AMOUNT:
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

    private record LogEntry(EntityId senderId, EntityId receiverId, long amount) {}

    /**
     * Returns the number of transfers (accounts) needed for the given transfer type.
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
