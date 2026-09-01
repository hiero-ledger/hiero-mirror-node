// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.RecordItemBuilder.DEFAULT_GAS_USED;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
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
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.exception.ParserException;
import org.hiero.mirror.importer.parser.domain.RecordFileBuilder;
import org.hiero.mirror.importer.parser.record.ethereum.LegacyEthereumTransactionParserTest;
import org.hiero.mirror.importer.repository.ContractLogRepository;
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
import reactor.test.StepVerifier;

@RequiredArgsConstructor
class RecordFileParserIntegrationTest extends ImporterIntegrationTest {

    private final CryptoTransferRepository cryptoTransferRepository;
    private final EntityRepository entityRepository;
    private final ReactiveRedisOperations<String, StreamMessage> reactiveRedisOperations;
    private final RecordFileBuilder recordFileBuilder;
    private final RecordItemBuilder recordItemBuilder;
    private final RecordFileParser recordFileParser;
    private final RecordFileRepository recordFileRepository;
    private final TransactionRepository transactionRepository;
    private final ContractLogRepository contractLogRepository;

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

    /**
     * End-to-end test replaying Hedera mainnet block 97776120 (0x5d3f1f8). The block contains, in transaction order:
     * a successful legacy Ethereum transaction with four logs, four plain crypto transfers, a successful legacy
     * Ethereum transaction with five logs that spawned four child transactions with their own contract results
     * (excluded from the receipts trie; the child contract calls inherit the parent's EVM transaction index), a
     * failed (WRONG_NONCE) Ethereum transaction (which claims no EVM transaction index and is excluded) and a crypto
     * approve allowance producing a synthetic approval log. Under EVM-only transaction indexing the receipts sit at
     * trie keys 0, 1 and 2 (the synthetic receipt). All receipt-relevant values (gas, blooms, log
     * addresses/topics/data, entity ids) mirror the mainnet block; the expected root was computed with an independent
     * implementation (@ethereumjs/trie and @ethereumjs/rlp) over those receipts. It intentionally differs from the
     * 0xd0795716... root the relay served for this block, whose calculation keyed receipts by the block-wide
     * transaction index.
     */
    @Test
    void receiptsRootOfReplayedMainnetBlock() {
        // given contracts with EVM address aliases referenced by the block's logs
        persistContract(9469373L, "f09afe78d3c7d359b334d7cb88995751f7ec5e13");
        persistContract(10603948L, "47cbb1f75fa2a98a87f861c5039fcb522b93a640");
        persistContract(3964804L, "c5b707348da504e9be1bd4e21525459830e7b11d");

        var baseTimestamp = domainBuilder.timestamp();
        var items = new ArrayList<RecordItem>();
        var previous = buildItem(
                items,
                ethereumTransactionItem(
                        130465L,
                        9469373L,
                        BLOOM_TX_0,
                        List.of(
                                logInfo(
                                        10603948L,
                                        "00000000000000000000000000000000000000000000000071a7432fce49c000"
                                                + "000000000000000000000000000000000000000000000000000000006a59dc96",
                                        "52f50aa6d1a95a4595361ecf953d095f125d442e4673716dede699e049de148a",
                                        "0000000000000000000000007ce6bb2cc2d3fd45a974da6a0f29236cb9513a98"),
                                logInfo(
                                        10603948L,
                                        "0000000000000000000000000000000000000000022373a84bf1629d14e00000"
                                                + "000000000000000000000000000000000000000000000000000000006a59dc96",
                                        "52f50aa6d1a95a4595361ecf953d095f125d442e4673716dede699e049de148a",
                                        "000000000000000000000000b1f616b8134f602c3bb465fb5b5e6565ccad37ed"),
                                logInfo(
                                        9469373L,
                                        "0000000000000000000000000000000000000000000000000000000000000060"
                                                + "0000000000000000000000000000000000000000000000000000000000000080"
                                                + "00000000000000000000000000000000000000000000000000000000000000a0"
                                                + "0000000000000000000000000000000000000000000000000000000000000000"
                                                + "0000000000000000000000000000000000000000000000000000000000000000"
                                                + "0000000000000000000000000000000000000000000000000000000000000040"
                                                + "00000000000000000000000000000000000000000000000000000000000000e0"
                                                + "0000000000000000000000000000000000000000000000000000000000000002"
                                                + "0000000000000000000000007ce6bb2cc2d3fd45a974da6a0f29236cb9513a98"
                                                + "00000000000000000000000000000000000000000000000071a7432fce49c000"
                                                + "000000000000000000000000b1f616b8134f602c3bb465fb5b5e6565ccad37ed"
                                                + "0000000000000000000000000000000000000000022373a84bf1629d14e00000"
                                                + "0000000000000000000000000000000000000000000000000000000000000000",
                                        "b967c9b9e1b7af9a61ca71ff00e9f5b89ec6f2e268de8dacf12f0de8e51f3e47"),
                                logInfo(
                                        9469373L,
                                        "000ad45374fd3ec68ae6c31741ec5c0f705c103f68ec76a31bf20101032a8e1c"
                                                + "000000000000000000000000000000000000000000000000000000000003f954",
                                        "198d6990ef96613a9026203077e422916918b03ff47f0be6bee7b02d8e139ef0",
                                        "0000000000000000000000000000000000000000000000000000000000000000"))),
                baseTimestamp,
                null,
                null,
                0);
        for (int i = 1; i <= 4; i++) {
            previous = buildItem(items, recordItemBuilder.cryptoTransfer(), baseTimestamp + i, null, previous, i);
        }
        previous = buildItem(
                items,
                ethereumTransactionItem(
                        142621L,
                        3949434L,
                        BLOOM_TX_5,
                        List.of(
                                logInfo(
                                        456858L,
                                        "00000000000000000000000000000000000000000000000000000000274af988",
                                        "ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef",
                                        "000000000000000000000000c5b707348da504e9be1bd4e21525459830e7b11d",
                                        "0000000000000000000000003da633d7e99e590a6c6fbe392921e087e1f6d423"),
                                logInfo(
                                        3964804L,
                                        "00000000000000000000000000000000000000000000000000000000274af988",
                                        "5f2147fb558c977441fbdfebcf8cd5776606adc8da5ff95566fc2a4137e54d13",
                                        "000000000000000000000000c5b707348da504e9be1bd4e21525459830e7b11d",
                                        "0000000000000000000000003da633d7e99e590a6c6fbe392921e087e1f6d423",
                                        "000000000000000000000000000000000000000000000000000000000006f89a"),
                                logInfo(
                                        1456986L,
                                        "000000000000000000000000000000000000000000000000000000e8d4a51000",
                                        "ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef",
                                        "0000000000000000000000003da633d7e99e590a6c6fbe392921e087e1f6d423",
                                        "000000000000000000000000c5b707348da504e9be1bd4e21525459830e7b11d"),
                                logInfo(
                                        3949434L,
                                        "000000000000000000000000000000000000000000000000000000e8d4a51000",
                                        "5f2147fb558c977441fbdfebcf8cd5776606adc8da5ff95566fc2a4137e54d13",
                                        "0000000000000000000000003da633d7e99e590a6c6fbe392921e087e1f6d423",
                                        "000000000000000000000000c5b707348da504e9be1bd4e21525459830e7b11d",
                                        "0000000000000000000000000000000000000000000000000000000000163b5a"),
                                logInfo(
                                        3964804L,
                                        "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffd8b50678"
                                                + "000000000000000000000000000000000000000000000000000000e8d4a51000"
                                                + "0000000000000000000000000000000000000026eb79c1729d30dce470eec791"
                                                + "00000000000000000000000000000000000000000000000000017239172efa3f"
                                                + "0000000000000000000000000000000000000000000000000000000000011e11",
                                        "c42079f94a6350d7e6235f29174924f928cc2ac818eb64fed8004e115fbcca67",
                                        "00000000000000000000000000000000000000000000000000000000003c437a",
                                        "0000000000000000000000003da633d7e99e590a6c6fbe392921e087e1f6d423"))),
                baseTimestamp + 5,
                null,
                previous,
                5);
        var parentTimestamp = baseTimestamp + 5;
        previous = buildItem(
                items,
                childItem(recordItemBuilder.cryptoTransfer(), 1, 15284L),
                baseTimestamp + 6,
                parentTimestamp,
                previous,
                6);
        previous = buildItem(
                items,
                childItem(recordItemBuilder.contractCall(), 2, 2607L),
                baseTimestamp + 7,
                parentTimestamp,
                previous,
                7);
        previous = buildItem(
                items,
                childItem(recordItemBuilder.cryptoTransfer(), 3, 15284L),
                baseTimestamp + 8,
                parentTimestamp,
                previous,
                8);
        previous = buildItem(
                items,
                childItem(recordItemBuilder.contractCall(), 4, 2607L),
                baseTimestamp + 9,
                parentTimestamp,
                previous,
                9);
        previous = buildItem(
                items,
                ethereumTransactionItem(0L, 7646340L, new byte[0], List.of())
                        .receipt(r -> r.setStatus(ResponseCodeEnum.WRONG_NONCE)),
                baseTimestamp + 10,
                null,
                previous,
                10);
        buildItem(
                items,
                recordItemBuilder.cryptoApproveAllowance().transactionBody(b -> b.clearCryptoAllowances()
                        .clearNftAllowances()
                        .clearTokenAllowances()
                        .addTokenAllowances(com.hederahashgraph.api.proto.java.TokenAllowance.newBuilder()
                                .setOwner(AccountID.newBuilder().setAccountNum(1339713L))
                                .setSpender(AccountID.newBuilder().setAccountNum(4053945L))
                                .setTokenId(TokenID.newBuilder().setTokenNum(731861L))
                                .setAmount(240595193L))),
                baseTimestamp + 11,
                null,
                previous,
                11);

        var recordFile = domainBuilder
                .recordFile()
                .customize(r -> r.consensusStart(baseTimestamp)
                        .consensusEnd(baseTimestamp + 11)
                        .count(12L)
                        .index(0L)
                        .items(items))
                .get();

        // when
        recordFileParser.parse(recordFile);

        // then
        assertThat(recordFileRepository.findById(recordFile.getConsensusEnd()))
                .isPresent()
                .get()
                .extracting(RecordFile::getReceiptsRoot)
                .isEqualTo(HexFormat.of().parseHex("498f3a4b734e4a265438f8e346b06d2ef0f10d7596fda65c0d106b28ef3dd1bc"));
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

    private static final byte[] BLOOM_TX_0 = HexFormat.of()
            .parseHex("0000000000000000000000000000000000000000000000008000000000000000"
                    + "0000000020000000000000000000000000000000000000000000000020000a00"
                    + "0000000000000000000000000004000000002000000001000000000000000001"
                    + "0000000002000000000000000000080000000000000000000000000000000100"
                    + "0000000000010000000000000000000000000000000000000000000000000001"
                    + "0000000000000000000000000000000000000000000080000000000002000000"
                    + "0000000000000004000000000000000000000000000000001000000020002000"
                    + "0000000800000000000010000000000000000001000000008000000000000000");

    private static final byte[] BLOOM_TX_5 = HexFormat.of()
            .parseHex("0000208000000000200000000000000000000000000002010000000000000000"
                    + "0000000000000000800000000000000000000000010020000000000000020040"
                    + "0000400000002008000000080000000000000080000000000000000000000000"
                    + "0000000000000000000000000020000000000000000000000020001000088000"
                    + "0000000000000000000800008000000000000000000000000000200000000000"
                    + "0000002000040000000000000000000000000002000000000000000000000000"
                    + "0008000300000000000000000000000000020080000000000000002000000000"
                    + "0000000000000004000000000000000000000000000002000010000000800000");

    private static final long HTS_PRECOMPILE_CONTRACT_NUM = 359L;

    private void persistContract(long num, String evmAddress) {
        domainBuilder
                .entity()
                .customize(e -> e.id(num).num(num).evmAddress(HexFormat.of().parseHex(evmAddress)))
                .persist();
    }

    private RecordItem buildItem(
            List<RecordItem> items,
            RecordItemBuilder.Builder<?> builder,
            long consensusTimestamp,
            Long parentConsensusTimestamp,
            RecordItem previous,
            int transactionIndex) {
        builder.record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(consensusTimestamp)))
                .recordItem(r -> r.previous(previous).transactionIndex(transactionIndex));
        if (parentConsensusTimestamp != null) {
            builder.record(r -> r.setParentConsensusTimestamp(TestUtils.toTimestamp(parentConsensusTimestamp)));
        }
        var recordItem = builder.build();
        items.add(recordItem);
        return recordItem;
    }

    private RecordItemBuilder.Builder<?> ethereumTransactionItem(
            long gasUsed, long contractNum, byte[] bloom, List<ContractLoginfo> logs) {
        var contractId = ContractID.newBuilder().setContractNum(contractNum).build();
        var functionResult = ContractFunctionResult.newBuilder()
                .setContractID(contractId)
                .setGasUsed(gasUsed)
                .setBloom(DomainUtils.fromBytes(bloom))
                .addAllLogInfo(logs)
                .build();
        return recordItemBuilder
                .ethereumTransaction()
                .transactionBody(b -> b.setEthereumData(ByteString.copyFrom(
                        HexFormat.of().parseHex(LegacyEthereumTransactionParserTest.LEGACY_RAW_TX))))
                .receipt(r -> r.setContractID(contractId))
                .record(r -> r.setContractCallResult(functionResult));
    }

    private RecordItemBuilder.Builder<?> childItem(RecordItemBuilder.Builder<?> builder, int nonce, long gasUsed) {
        var contractId = ContractID.newBuilder()
                .setContractNum(HTS_PRECOMPILE_CONTRACT_NUM)
                .build();
        var functionResult = ContractFunctionResult.newBuilder()
                .setContractID(contractId)
                .setGasUsed(gasUsed)
                .build();
        var transactionId = TransactionID.newBuilder()
                .setNonce(nonce)
                .setAccountID(recordItemBuilder.accountId())
                .setScheduled(false);
        return builder.record(r -> r.setContractCallResult(functionResult).setTransactionID(transactionId));
    }

    private ContractLoginfo logInfo(long contractNum, String data, String... topics) {
        var builder = ContractLoginfo.newBuilder()
                .setContractID(ContractID.newBuilder().setContractNum(contractNum))
                .setData(ByteString.copyFrom(HexFormat.of().parseHex(data)));
        for (var topic : topics) {
            builder.addTopic(ByteString.copyFrom(HexFormat.of().parseHex(topic)));
        }
        return builder.build();
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
