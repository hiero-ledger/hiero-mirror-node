// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.RecordItemBuilder.DEFAULT_GAS_USED;

import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.RecordItemBuilder;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.topic.StreamMessage;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.common.util.LogsBloomFilter;
import org.hiero.mirror.importer.EnabledIfV1;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.exception.ParserException;
import org.hiero.mirror.importer.parser.domain.RecordFileBuilder;
import org.hiero.mirror.importer.repository.ContractLogRepository;
import org.hiero.mirror.importer.repository.ContractResultRepository;
import org.hiero.mirror.importer.repository.CryptoTransferRepository;
import org.hiero.mirror.importer.repository.EntityRepository;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.hiero.mirror.importer.repository.TransactionRepository;
import org.hiero.mirror.importer.test.performance.PerformanceProperties.SubType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.ReactiveSubscription.Message;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.util.Version;
import reactor.test.StepVerifier;

@RequiredArgsConstructor
class RecordFileParserIntegrationTest extends ImporterIntegrationTest {

    private static final Version HAPI_0_70 = new Version(0, 70, 0);
    private static final ContractID HOOK_CONTRACT_ID =
            ContractID.newBuilder().setContractNum(RecordItem.HOOK_CONTRACT_NUM).build();
    private static final ContractID HTS_PRECOMPILE_CONTRACT_ID =
            ContractID.newBuilder().setContractNum(0x167).build();

    private final CryptoTransferRepository cryptoTransferRepository;
    private final EntityRepository entityRepository;
    private final ReactiveRedisOperations<String, StreamMessage> reactiveRedisOperations;
    private final RecordFileBuilder recordFileBuilder;
    private final RecordItemBuilder recordItemBuilder;
    private final RecordFileParser recordFileParser;
    private final RecordFileRepository recordFileRepository;
    private final TransactionRepository transactionRepository;
    private final ContractLogRepository contractLogRepository;
    private final ContractResultRepository contractResultRepository;

    @BeforeEach
    void setup() {
        recordFileParser.clear();
    }

    @Test
    void parse() {
        // given
        int transactions = 100;
        int entities = 50;
        var recordFileTemplate = recordFileBuilder
                .recordFile()
                .recordItems(i -> i.count(transactions).entities(entities).type(TransactionType.CRYPTOTRANSFER));
        var recordFile1 = recordFileTemplate.build();
        var recordFile2 = recordFileTemplate.build();

        // when
        recordFileParser.parse(recordFile1);
        recordFileParser.parse(recordFile2);

        // then
        assertRecordFile(recordFile1, recordFile2);
        assertThat(cryptoTransferRepository.count()).isEqualTo(2 * 5 * transactions);
        assertThat(entityRepository.count()).isZero(); // Partial entities ignored
        assertThat(transactionRepository.count()).isEqualTo(2 * transactions);
    }

    @Test
    void parseList() {
        // given
        int transactions = 100;
        int entities = 50;
        var recordFileTemplate = recordFileBuilder
                .recordFile()
                .recordItems(i -> i.count(transactions).entities(entities).type(TransactionType.CRYPTOTRANSFER));
        var recordFile1 = recordFileTemplate.build();
        var recordFile2 = recordFileTemplate.build();

        // when
        recordFileParser.parse(List.of(recordFile1, recordFile2));

        // then
        assertRecordFile(recordFile1, recordFile2);
        assertThat(cryptoTransferRepository.count()).isEqualTo(2 * 5 * transactions);
        assertThat(transactionRepository.count()).isEqualTo(2 * transactions);
    }

    @Test
    void parseSingleThenList() {
        // given
        int transactions = 100;
        int entities = 50;
        var recordFileTemplate = recordFileBuilder
                .recordFile()
                .recordItems(i -> i.count(transactions).entities(entities).type(TransactionType.CRYPTOTRANSFER));
        var recordFile1 = recordFileTemplate.build();
        var recordFile2 = recordFileTemplate.build();
        var recordFile3 = recordFileTemplate.build();

        // when
        recordFileParser.parse(recordFile1);
        recordFileParser.parse(List.of(recordFile2, recordFile3));

        // then
        assertRecordFile(recordFile1, recordFile2, recordFile3);
        assertThat(cryptoTransferRepository.count()).isEqualTo(3 * 5 * transactions);
        assertThat(transactionRepository.count()).isEqualTo(3 * transactions);
    }

    @Test
    void parseSingleFileWithNonceZeroContractCallItems() {
        // given
        int transactions = 2;
        int entities = 2;
        final var logsBloom = new LogsBloomFilter();
        var recordFileTemplate = recordFileBuilder
                .recordFile()
                .recordItems(i -> i.count(transactions).entities(entities).subType(SubType.CONTRACT_CALL));
        var recordFile = recordFileTemplate.build();
        recordFile.getItems().forEach(r -> {
            var rec = r.getTransactionRecord();
            var result = rec.hasContractCreateResult() ? rec.getContractCreateResult() : rec.getContractCallResult();
            logsBloom.or(DomainUtils.toBytes(result.getBloom()));
        });

        // when
        recordFileParser.parse(recordFile);

        // then
        assertRecordFile(recordFile);
        var updatedRecordFileOptional = recordFileRepository.findById(recordFile.getConsensusEnd());
        assertThat(updatedRecordFileOptional).isPresent();
        var updatedRecordFile = updatedRecordFileOptional.get();
        assertThat(updatedRecordFile.getGasUsed()).isEqualTo(transactions * DEFAULT_GAS_USED);
        assertThat(updatedRecordFile.getLogsBloom()).isEqualTo(logsBloom.toArrayUnsafe());
    }

    @Test
    void parseSingleFileWithEmptyParentConsensusTimestampTopLevelContractCallItems() {
        // given
        int transactions = 2;
        int entities = 2;
        final var logsBloom = new LogsBloomFilter();
        var recordFileTemplate = recordFileBuilder.recordFile().recordItems(i -> i.count(transactions)
                .entities(entities)
                .subType(SubType.CONTRACT_CALL)
                .nonce(5)
                .isScheduled(false));
        var recordFile = recordFileTemplate.build();
        recordFile.getItems().forEach(r -> {
            var rec = r.getTransactionRecord();
            var result = rec.hasContractCreateResult() ? rec.getContractCreateResult() : rec.getContractCallResult();
            logsBloom.or(DomainUtils.toBytes(result.getBloom()));
        });

        // when
        recordFileParser.parse(recordFile);

        // then
        assertRecordFile(recordFile);
        var updatedRecordFileOptional = recordFileRepository.findById(recordFile.getConsensusEnd());
        assertThat(updatedRecordFileOptional).isPresent();
        var updatedRecordFile = updatedRecordFileOptional.get();
        assertThat(updatedRecordFile.getGasUsed()).isEqualTo(transactions * DEFAULT_GAS_USED);
        assertThat(updatedRecordFile.getLogsBloom()).isEqualTo(logsBloom.toArrayUnsafe());
    }

    @Test
    void parseSingleFileWitHavingOneTopLevelAndOneNotTopLevelContractCallItems() {
        // given
        int transactions = 2;
        int entities = 2;
        final var logsBloom = new LogsBloomFilter();
        var recordFileTemplate = recordFileBuilder.recordFile().recordItems(i -> i.count(transactions)
                .entities(entities)
                .subType(SubType.CONTRACT_CALL)
                .nonce(5)
                .isScheduled(false)
                .parentConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(1403434L).build()));
        var recordFile = recordFileTemplate.build();
        recordFile.getItems().forEach(r -> {
            var rec = r.getTransactionRecord();
            var result = rec.hasContractCreateResult() ? rec.getContractCreateResult() : rec.getContractCallResult();
            logsBloom.or(DomainUtils.toBytes(result.getBloom()));
        });

        // when
        recordFileParser.parse(recordFile);

        // then
        assertRecordFile(recordFile);
        var updatedRecordFileOptional = recordFileRepository.findById(recordFile.getConsensusEnd());
        assertThat(updatedRecordFileOptional).isPresent();
        var updatedRecordFile = updatedRecordFileOptional.get();
        assertThat(updatedRecordFile.getGasUsed()).isEqualTo(DEFAULT_GAS_USED);
        assertThat(updatedRecordFile.getLogsBloom()).isEqualTo(logsBloom.toArrayUnsafe());
    }

    @Test
    void parseSingleFileWithPositiveNonceAndScheduledTopLevelContractCallItems() {
        // given
        int transactions = 2;
        int entities = 2;
        final var logsBloom = new LogsBloomFilter();
        var recordFileTemplate = recordFileBuilder.recordFile().recordItems(i -> i.count(transactions)
                .entities(entities)
                .subType(SubType.CONTRACT_CALL)
                .nonce(8)
                .isScheduled(true));
        var recordFile = recordFileTemplate.build();
        recordFile.getItems().forEach(r -> {
            var rec = r.getTransactionRecord();
            var result = rec.hasContractCreateResult() ? rec.getContractCreateResult() : rec.getContractCallResult();
            logsBloom.or(DomainUtils.toBytes(result.getBloom()));
        });

        // when
        recordFileParser.parse(recordFile);

        // then
        assertRecordFile(recordFile);
        var updatedRecordFileOptional = recordFileRepository.findById(recordFile.getConsensusEnd());
        assertThat(updatedRecordFileOptional).isPresent();
        var updatedRecordFile = updatedRecordFileOptional.get();
        assertThat(updatedRecordFile.getGasUsed()).isEqualTo(transactions * DEFAULT_GAS_USED);
        assertThat(updatedRecordFile.getLogsBloom()).isEqualTo(logsBloom.toArrayUnsafe());
    }

    @Test
    void parseSingleFileWithOneTopLevelAndOneNotTopLevelContractCall() {
        // given
        int transactions = 2;
        int entities = 1;
        final var logsBloom = new LogsBloomFilter();
        var recordFileTemplate = recordFileBuilder.recordFile().recordItems(i -> i.count(transactions)
                .entities(entities)
                .subType(SubType.CONTRACT_CALL)
                .nonce(5)
                .isScheduled(false)
                .parentConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(1403434L).build()));
        var recordFile = recordFileTemplate.build();
        var transactionRecord = recordFile.getItems().get(1).getTransactionRecord();
        var result = transactionRecord.hasContractCreateResult()
                ? transactionRecord.getContractCreateResult()
                : transactionRecord.getContractCallResult();
        logsBloom.or(DomainUtils.toBytes(result.getBloom()));

        // when
        recordFileParser.parse(recordFile);

        // then
        assertRecordFile(recordFile);
        var updatedRecordFileOptional = recordFileRepository.findById(recordFile.getConsensusEnd());
        assertThat(updatedRecordFileOptional).isPresent();
        var updatedRecordFile = updatedRecordFileOptional.get();
        assertThat(updatedRecordFile.getGasUsed()).isEqualTo(entities * DEFAULT_GAS_USED);
        assertThat(updatedRecordFile.getLogsBloom()).isEqualTo(logsBloom.toArrayUnsafe());
    }

    @Test
    void parseSingleFileWithSystemFileUpdateTopLevelItems() {
        // given
        int transactions = 3;
        var recordFile = recordFileBuilder
                .recordFile()
                .recordItems(i -> i.count(transactions)
                        .template(() -> recordItemBuilder.fileUpdate().record(r -> r.getTransactionIDBuilder()
                                .setNonce(7)
                                .setScheduled(false)
                                .setAccountID(com.hederahashgraph.api.proto.java.AccountID.newBuilder()
                                        .setAccountNum(50)))))
                .build();

        // when
        recordFileParser.parse(recordFile);

        // then
        assertRecordFile(recordFile);
        var updatedRecordFileOptional = recordFileRepository.findById(recordFile.getConsensusEnd());
        assertThat(updatedRecordFileOptional).isPresent();
        var updatedRecordFile = updatedRecordFileOptional.get();
        assertThat(updatedRecordFile.getGasUsed()).isZero();
        assertThat(updatedRecordFile.getLogsBloom()).isEmpty();
    }

    @Test
    void parseWithLogIndexValidation() {
        // given
        int transactions = 10;
        var recordFileTemplate = recordFileBuilder.recordFile().recordItems(i -> i.count(transactions)
                .type(TransactionType.CONTRACTCALL));
        var recordFile = recordFileTemplate.build();

        // when
        recordFileParser.parse(recordFile);

        // then
        assertThat(recordFileRepository.findAll()).hasSize(1);
        final var contractLogs = contractLogRepository.findAll();
        assertThat(contractLogs).hasSize(transactions * 2);
        final var contractLogsList = new LinkedList<ContractLog>();
        contractLogs.forEach(contractLogsList::add);
        contractLogsList.sort(
                Comparator.comparing(ContractLog::getConsensusTimestamp).thenComparing(ContractLog::getIndex));

        final var index = new AtomicInteger();
        contractLogsList.forEach(cl -> {
            assertThat(cl.getIndex()).isEqualTo(index.getAndIncrement());
        });
    }

    @Test
    void topicMessage() {
        int count = 3;
        var topicMessage = recordItemBuilder.consensusSubmitMessage();
        var topicId = EntityId.of(topicMessage
                .build()
                .getTransactionBody()
                .getConsensusSubmitMessage()
                .getTopicID());
        var recordFile = recordFileBuilder
                .recordFile()
                .recordItems(i -> i.count(count).template(() -> topicMessage))
                .build();

        var receive = reactiveRedisOperations
                .listenToChannel("topic." + topicId.getId())
                .map(Message::getMessage);
        StepVerifier.create(receive)
                .thenAwait(java.time.Duration.ofSeconds(1L))
                .then(() -> recordFileParser.parse(recordFile))
                .thenAwait(java.time.Duration.ofMillis(500L))
                .expectNextCount(count)
                .thenCancel()
                .verify(java.time.Duration.ofMillis(2000L));
        assertRecordFile(recordFile);
    }

    @Test
    void parseHookDrivenTransferWithEmptyFileBloom() {
        final var items = hookDrivenTransferItems();
        final var recordFile = recordFileWithItems(items, null);

        recordFileParser.parse(recordFile);

        assertRecordFile(recordFile);
        final var updatedRecordFile =
                recordFileRepository.findById(recordFile.getConsensusEnd()).orElseThrow();
        final var hookContractResult = contractResultRepository
                .findById(items.get(1).getConsensusTimestamp())
                .orElseThrow();

        assertThat(contractLogRepository.findAll()).isNotEmpty();
        assertThat(updatedRecordFile.getLogsBloom())
                .hasSize(LogsBloomFilter.BYTE_SIZE)
                .isNotEqualTo(new byte[LogsBloomFilter.BYTE_SIZE])
                .isEqualTo(hookContractResult.getBloom());
    }

    @Test
    void parseBatchLastFileHasNoTopLevelContractTransactions() {
        final var firstFileItems = htsPrecompileChildUnderTopLevelContractCall();
        final var firstFile = recordFileWithItems(firstFileItems, null);
        final var lastFile = recordFileBuilder
                .recordFile()
                .previous(firstFile)
                .recordItem(TransactionType.CRYPTOTRANSFER)
                .build();

        recordFileParser.parse(List.of(firstFile, lastFile));

        assertThat(recordFileRepository.findAll()).hasSize(2);
        assertThat(contractLogRepository.findAll()).isNotEmpty();
        final var lastPersisted =
                recordFileRepository.findById(lastFile.getConsensusEnd()).orElseThrow();
        assertThat(lastPersisted.getLogsBloom()).hasSize(LogsBloomFilter.BYTE_SIZE);
    }

    @Test
    @EnabledIfV1
    void rollback() {
        // when
        var recordFileTemplate = recordFileBuilder.recordFile().recordItem(TransactionType.CRYPTOTRANSFER);
        var recordFile1 = recordFileTemplate.build();
        var items = recordFile1.getItems();
        recordFileParser.parse(recordFile1);

        // then
        assertRecordFile(recordFile1);

        // when
        var recordFile2 = recordFileTemplate.build();
        recordFile2.setItems(items); // Re-processing same transactions should result in duplicate keys
        Assertions.assertThrows(ParserException.class, () -> recordFileParser.parse(recordFile2));

        // then
        assertRecordFile(recordFile1);
        assertThat(retryRecorder.getRetries(ParserException.class)).isEqualTo(2);
    }

    private List<RecordItem> hookDrivenTransferItems() {
        final var topLevel = recordItemBuilder
                .cryptoTransfer()
                .recordItem(r -> r.hapiVersion(HAPI_0_70))
                .build();
        final var hookCall = recordItemBuilder
                .contractCall(HOOK_CONTRACT_ID)
                .record(r -> r.setTransactionID(r.getTransactionID().toBuilder().setNonce(1))
                        .setContractCallResult(
                                ContractFunctionResult.newBuilder().setContractID(HOOK_CONTRACT_ID))
                        .setParentConsensusTimestamp(
                                topLevel.getTransactionRecord().getConsensusTimestamp()))
                .recordItem(r -> r.previous(topLevel).hapiVersion(HAPI_0_70))
                .build();
        return List.of(topLevel, hookCall, htsPrecompileChild(topLevel, hookCall, 2));
    }

    private List<RecordItem> htsPrecompileChildUnderTopLevelContractCall() {
        final var topLevel = recordItemBuilder
                .contractCall()
                .recordItem(r -> r.hapiVersion(HAPI_0_70))
                .build();
        return List.of(topLevel, htsPrecompileChild(topLevel, topLevel, 1));
    }

    private RecordItem htsPrecompileChild(final RecordItem topLevel, final RecordItem previous, final int nonce) {
        final var tokenTransfers = TokenTransferList.newBuilder()
                .setToken(recordItemBuilder.tokenId())
                .addTransfers(recordItemBuilder.accountAmount(recordItemBuilder.accountId(), -100))
                .addTransfers(recordItemBuilder.accountAmount(recordItemBuilder.accountId(), 100));
        return recordItemBuilder
                .contractCall(HTS_PRECOMPILE_CONTRACT_ID)
                .record(r -> r.setTransactionID(r.getTransactionID().toBuilder().setNonce(nonce))
                        .setContractCallResult(
                                ContractFunctionResult.newBuilder().setContractID(HTS_PRECOMPILE_CONTRACT_ID))
                        .setParentConsensusTimestamp(
                                topLevel.getTransactionRecord().getConsensusTimestamp())
                        .addTokenTransferLists(tokenTransfers))
                .recordItem(r -> r.previous(previous).hapiVersion(HAPI_0_70))
                .build();
    }

    private RecordFile recordFileWithItems(final List<RecordItem> items, final RecordFile previous) {
        return domainBuilder
                .recordFile()
                .customize(r -> {
                    r.items(items)
                            .consensusStart(items.getFirst().getConsensusTimestamp())
                            .consensusEnd(items.getLast().getConsensusTimestamp())
                            .count((long) items.size())
                            .sidecarCount(0)
                            .sidecars(List.of());
                    if (previous != null) {
                        r.index(previous.getIndex() + 1).previousHash(previous.getHash());
                    }
                })
                .get();
    }

    private void assertRecordFile(RecordFile... recordFiles) {
        assertThat(recordFileRepository.findAll())
                .hasSize(recordFiles.length)
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("bytes", "items", "logsBloom", "sidecars")
                .containsExactlyInAnyOrder(recordFiles)
                .allSatisfy(rf -> {
                    assertThat(rf.getLoadStart()).isPositive();
                    assertThat(rf.getLoadEnd()).isPositive();
                    assertThat(rf.getLoadEnd()).isGreaterThanOrEqualTo(rf.getLoadStart());
                });
    }
}
