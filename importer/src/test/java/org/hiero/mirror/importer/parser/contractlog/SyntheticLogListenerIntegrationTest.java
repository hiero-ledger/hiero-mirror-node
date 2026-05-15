// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.trim;
import static org.hiero.mirror.importer.parser.contractlog.SyntheticLogTestUtils.aggregateExpectedContractResultBloom;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import com.google.common.primitives.Longs;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.util.LogsBloomFilter;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.parser.record.RecordStreamFileListener;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.repository.ContractLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

@RequiredArgsConstructor
public class SyntheticLogListenerIntegrationTest extends ImporterIntegrationTest {
    private final RecordStreamFileListener recordFileStreamListener;
    private final DomainBuilder domainBuilder;
    private final TransactionTemplate transactionTemplate;
    private final ContractLogRepository contractLogRepository;
    private final EntityListener entityListener;
    private final org.hiero.mirror.importer.parser.record.entity.ParserContext parserContext;

    @Test
    void nonTransferContractLog() {
        var sender1 = domainBuilder.entity().persist();
        var receiver1 = domainBuilder.entity().persist();

        var contractLog = domainBuilder
                .contractLog()
                .customize(cl ->
                        cl.topic1(Longs.toByteArray(sender1.getNum())).topic2(Longs.toByteArray(receiver1.getNum())))
                .get();

        entityListener.onContractLog(contractLog);

        assertArrayEquals(Longs.toByteArray(sender1.getNum()), contractLog.getTopic1());
        assertArrayEquals(Longs.toByteArray(receiver1.getNum()), contractLog.getTopic2());

        completeFileAndCommit();

        assertArrayEquals(Longs.toByteArray(sender1.getNum()), contractLog.getTopic1());
        assertArrayEquals(Longs.toByteArray(receiver1.getNum()), contractLog.getTopic2());
        assertThat(contractLogRepository.count()).isEqualTo(1);
        assertThat(contractLogRepository.findAll()).containsExactly(contractLog);
    }

    @Test
    void transferContractLog() {
        var sender1 = domainBuilder.entity().persist();
        var receiver1 = domainBuilder.entity().persist();

        var contractLog = domainBuilder
                .contractLog()
                .customize(cl -> cl.topic1(AbstractSyntheticContractLog.entityIdToBytes(EntityId.of(sender1.getId())))
                        .topic2(AbstractSyntheticContractLog.entityIdToBytes(EntityId.of(receiver1.getId())))
                        .synthetic(true))
                .get();

        entityListener.onContractLog(contractLog);

        assertArrayEquals(
                AbstractSyntheticContractLog.entityIdToBytes(EntityId.of(sender1.getId())), contractLog.getTopic1());
        assertArrayEquals(
                AbstractSyntheticContractLog.entityIdToBytes(EntityId.of(receiver1.getId())), contractLog.getTopic2());

        completeFileAndCommit();

        assertArrayEquals(trim(sender1.getEvmAddress()), contractLog.getTopic1());
        assertArrayEquals(trim(receiver1.getEvmAddress()), contractLog.getTopic2());
        assertThat(contractLogRepository.count()).isEqualTo(1);
        assertThat(contractLogRepository.findAll()).containsExactly(contractLog);
    }

    @Test
    void childContractExecutionWithAliasTopicsUsesParentTransactionHash() {
        var senderEvm = Bytes.fromHexString("0xd838319b64e38ca2ed8b08c9324ebe4d37facecf")
                .toArrayUnsafe();
        var receiverEvm = Bytes.fromHexString("0x2818870d1daa81b75e38cec44ceae51b4b684696")
                .toArrayUnsafe();
        var contractEvm = Bytes.fromHexString("0xabcdef1234567890abcdef1234567890abcdef12")
                .toArrayUnsafe();
        var parentTransactionHash = Bytes.fromHexString(
                        "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef")
                .toArrayUnsafe();

        var sender = domainBuilder
                .entity()
                .customize(e -> e.evmAddress(senderEvm).alias(senderEvm))
                .persist();
        var receiver = domainBuilder
                .entity()
                .customize(e -> e.evmAddress(receiverEvm).alias(receiverEvm))
                .persist();

        var contractEntity = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.CONTRACT).evmAddress(contractEvm))
                .persist();

        var senderEntityId = EntityId.of(sender.getId());
        var receiverEntityId = EntityId.of(receiver.getId());
        var topic1Before = AbstractSyntheticContractLog.entityIdToBytes(senderEntityId);
        var topic2Before = AbstractSyntheticContractLog.entityIdToBytes(receiverEntityId);

        var markerBloom = new byte[] {1};

        var syntheticContractLog = domainBuilder
                .contractLog()
                .customize(cl -> cl.synthetic(true)
                        .bloom(markerBloom)
                        .contractId(EntityId.of(contractEntity.getId()))
                        .topic0(AbstractSyntheticContractLog.TRANSFER_SIGNATURE)
                        .topic1(topic1Before)
                        .topic2(topic2Before)
                        .topic3(null)
                        .data(new byte[] {0})
                        .transactionHash(parentTransactionHash))
                .get();

        entityListener.onContractLog(syntheticContractLog);

        assertArrayEquals(markerBloom, syntheticContractLog.getBloom());
        assertArrayEquals(topic1Before, syntheticContractLog.getTopic1());
        assertArrayEquals(topic2Before, syntheticContractLog.getTopic2());
        assertArrayEquals(parentTransactionHash, syntheticContractLog.getTransactionHash());

        completeFileAndCommit();

        assertArrayEquals(trim(senderEvm), syntheticContractLog.getTopic1());
        assertArrayEquals(trim(receiverEvm), syntheticContractLog.getTopic2());

        var expectedBloom = new LogsBloomFilter();
        expectedBloom.insertAddress(trim(contractEvm));
        expectedBloom.insertTopic(AbstractSyntheticContractLog.TRANSFER_SIGNATURE);
        expectedBloom.insertTopic(syntheticContractLog.getTopic1());
        expectedBloom.insertTopic(syntheticContractLog.getTopic2());
        expectedBloom.insertTopic(syntheticContractLog.getTopic3());
        assertThat(syntheticContractLog.getBloom()).hasSize(LogsBloomFilter.BYTE_SIZE);
        assertArrayEquals(expectedBloom.toArrayUnsafe(), syntheticContractLog.getBloom());

        assertArrayEquals(parentTransactionHash, syntheticContractLog.getTransactionHash());

        assertThat(contractLogRepository.count()).isEqualTo(1);
        var persistedLogs = contractLogRepository.findAll();
        assertThat(persistedLogs).hasSize(1);
        var persistedLog = persistedLogs.iterator().next();
        assertArrayEquals(parentTransactionHash, persistedLog.getTransactionHash());
        assertArrayEquals(trim(senderEvm), persistedLog.getTopic1());
        assertArrayEquals(trim(receiverEvm), persistedLog.getTopic2());
    }

    private void completeFileAndCommit() {
        var recordFile =
                domainBuilder.recordFile().customize(r -> r.sidecars(List.of())).get();
        transactionTemplate.executeWithoutResult(status -> recordFileStreamListener.onEnd(recordFile));
    }

    @Test
    void recordFileBloomUnchangedWhenNoSyntheticLogs() {
        var existingBloom = domainBuilder.bloomFilter();
        var recordFile = domainBuilder
                .recordFile()
                .customize(r -> r.sidecars(List.of()).logsBloom(existingBloom))
                .get();

        parserContext.add(recordFile);

        transactionTemplate.executeWithoutResult(status -> {
            recordFileStreamListener.onEnd(recordFile);
            parserContext.clear();
        });

        assertArrayEquals(existingBloom, recordFile.getLogsBloom());
    }

    @Test
    void recordFileBloomMergesExistingAndSyntheticBlooms() {
        var sender = domainBuilder.entity().persist();
        var receiver = domainBuilder.entity().persist();
        var contractEntity = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.CONTRACT))
                .persist();

        var existingAddress = domainBuilder.evmAddress();
        var existingBloomFilter = new LogsBloomFilter();
        existingBloomFilter.insertAddress(existingAddress);
        var existingBloom = existingBloomFilter.toArrayUnsafe();

        var recordFile = domainBuilder
                .recordFile()
                .customize(r -> r.sidecars(List.of()).logsBloom(existingBloom))
                .get();

        parserContext.add(recordFile);

        var consensusTimestamp = domainBuilder.timestamp();
        var contractResult = domainBuilder
                .contractResult()
                .customize(cr -> cr.consensusTimestamp(consensusTimestamp)
                        .contractId(contractEntity.getId())
                        .payerAccountId(EntityId.of(domainBuilder.id()))
                        .bloom(existingBloom))
                .get();

        var markerBloom = new byte[] {1};
        var syntheticContractLog = domainBuilder
                .contractLog()
                .customize(cl -> cl.synthetic(true)
                        .bloom(markerBloom)
                        .consensusTimestamp(consensusTimestamp)
                        .contractId(EntityId.of(contractEntity.getId()))
                        .topic0(AbstractSyntheticContractLog.TRANSFER_SIGNATURE)
                        .topic1(AbstractSyntheticContractLog.entityIdToBytes(EntityId.of(sender.getId())))
                        .topic2(AbstractSyntheticContractLog.entityIdToBytes(EntityId.of(receiver.getId())))
                        .topic3(null)
                        .data(new byte[] {0})
                        .contractResult(contractResult))
                .get();

        entityListener.onContractLog(syntheticContractLog);

        var rfBloomBefore =
                recordFile.getLogsBloom() != null ? recordFile.getLogsBloom().clone() : null;

        transactionTemplate.executeWithoutResult(status -> {
            recordFileStreamListener.onEnd(recordFile);
            parserContext.clear();
        });

        assertArrayEquals(
                aggregateExpectedContractResultBloom(rfBloomBefore, contractResult), recordFile.getLogsBloom());
    }

    @Test
    void recordFileBloomSetWhenOnlySyntheticBloomAdded() {
        var sender = domainBuilder.entity().persist();
        var receiver = domainBuilder.entity().persist();
        var contractEntity = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.CONTRACT))
                .persist();

        var recordFile = domainBuilder
                .recordFile()
                .customize(r -> r.sidecars(List.of()).logsBloom(null))
                .get();

        parserContext.add(recordFile);

        var existingBloom = domainBuilder.bloomFilter();
        var consensusTimestamp = domainBuilder.timestamp();
        var contractResult = domainBuilder
                .contractResult()
                .customize(cr -> cr.consensusTimestamp(consensusTimestamp)
                        .contractId(contractEntity.getId())
                        .payerAccountId(EntityId.of(domainBuilder.id()))
                        .bloom(existingBloom))
                .get();

        var markerBloom = new byte[] {1};
        var syntheticContractLog = domainBuilder
                .contractLog()
                .customize(cl -> cl.synthetic(true)
                        .bloom(markerBloom)
                        .consensusTimestamp(consensusTimestamp)
                        .contractId(EntityId.of(contractEntity.getId()))
                        .topic0(AbstractSyntheticContractLog.TRANSFER_SIGNATURE)
                        .topic1(AbstractSyntheticContractLog.entityIdToBytes(EntityId.of(sender.getId())))
                        .topic2(AbstractSyntheticContractLog.entityIdToBytes(EntityId.of(receiver.getId())))
                        .topic3(null)
                        .data(new byte[] {0})
                        .contractResult(contractResult))
                .get();

        entityListener.onContractLog(syntheticContractLog);

        var rfBloomBefore =
                recordFile.getLogsBloom() != null ? recordFile.getLogsBloom().clone() : null;

        transactionTemplate.executeWithoutResult(status -> {
            recordFileStreamListener.onEnd(recordFile);
            parserContext.clear();
        });

        assertArrayEquals(
                aggregateExpectedContractResultBloom(rfBloomBefore, contractResult), recordFile.getLogsBloom());
    }

    @Test
    void recordFileBloomAggregatesMultipleSyntheticLogs() {
        var sender1 = domainBuilder.entity().persist();
        var receiver1 = domainBuilder.entity().persist();
        var sender2 = domainBuilder.entity().persist();
        var receiver2 = domainBuilder.entity().persist();
        var contractEntity = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.CONTRACT))
                .persist();

        var existingAddress = domainBuilder.evmAddress();
        var existingBloomFilter = new LogsBloomFilter();
        existingBloomFilter.insertAddress(existingAddress);
        var existingBloom = existingBloomFilter.toArrayUnsafe();

        var recordFile = domainBuilder
                .recordFile()
                .customize(r -> r.sidecars(List.of()).logsBloom(existingBloom))
                .get();

        parserContext.add(recordFile);

        var consensusTimestamp = domainBuilder.timestamp();
        var contractResult = domainBuilder
                .contractResult()
                .customize(cr -> cr.consensusTimestamp(consensusTimestamp)
                        .contractId(contractEntity.getId())
                        .payerAccountId(EntityId.of(domainBuilder.id()))
                        .bloom(existingBloom))
                .get();

        var markerBloom = new byte[] {1};

        var syntheticContractLog1 = domainBuilder
                .contractLog()
                .customize(cl -> cl.synthetic(true)
                        .bloom(markerBloom)
                        .consensusTimestamp(consensusTimestamp)
                        .contractId(EntityId.of(contractEntity.getId()))
                        .topic0(AbstractSyntheticContractLog.TRANSFER_SIGNATURE)
                        .topic1(AbstractSyntheticContractLog.entityIdToBytes(EntityId.of(sender1.getId())))
                        .topic2(AbstractSyntheticContractLog.entityIdToBytes(EntityId.of(receiver1.getId())))
                        .topic3(null)
                        .data(new byte[] {0})
                        .contractResult(contractResult))
                .get();

        var syntheticContractLog2 = domainBuilder
                .contractLog()
                .customize(cl -> cl.synthetic(true)
                        .bloom(markerBloom)
                        .consensusTimestamp(consensusTimestamp)
                        .contractId(EntityId.of(contractEntity.getId()))
                        .topic0(AbstractSyntheticContractLog.TRANSFER_SIGNATURE)
                        .topic1(AbstractSyntheticContractLog.entityIdToBytes(EntityId.of(sender2.getId())))
                        .topic2(AbstractSyntheticContractLog.entityIdToBytes(EntityId.of(receiver2.getId())))
                        .topic3(null)
                        .data(new byte[] {0})
                        .contractResult(contractResult))
                .get();

        entityListener.onContractLog(syntheticContractLog1);
        entityListener.onContractLog(syntheticContractLog2);

        var rfBloomBefore =
                recordFile.getLogsBloom() != null ? recordFile.getLogsBloom().clone() : null;

        transactionTemplate.executeWithoutResult(status -> {
            recordFileStreamListener.onEnd(recordFile);
            parserContext.clear();
        });

        assertArrayEquals(
                aggregateExpectedContractResultBloom(rfBloomBefore, contractResult), recordFile.getLogsBloom());
    }

    @Test
    void contractResultBloomMergesSyntheticBloom() {
        var sender = domainBuilder.entity().persist();
        var receiver = domainBuilder.entity().persist();
        var contractEntity = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.CONTRACT))
                .persist();

        var existingAddress = domainBuilder.evmAddress();
        var existingBloomFilter = new LogsBloomFilter();
        existingBloomFilter.insertAddress(existingAddress);
        var existingBloom = existingBloomFilter.toArrayUnsafe();

        var consensusTimestamp = domainBuilder.timestamp();

        var contractResult = domainBuilder
                .contractResult()
                .customize(cr -> cr.consensusTimestamp(consensusTimestamp)
                        .contractId(contractEntity.getId())
                        .payerAccountId(EntityId.of(domainBuilder.id()))
                        .bloom(existingBloom))
                .get();

        parserContext.add(contractResult, contractResult.getConsensusTimestamp());

        var recordFile = domainBuilder
                .recordFile()
                .customize(r -> r.sidecars(List.of()).logsBloom(existingBloom))
                .get();

        parserContext.add(recordFile);

        var markerBloom = new byte[] {1};
        var syntheticContractLog = domainBuilder
                .contractLog()
                .customize(cl -> cl.synthetic(true)
                        .bloom(markerBloom)
                        .consensusTimestamp(consensusTimestamp)
                        .contractId(EntityId.of(contractEntity.getId()))
                        .topic0(AbstractSyntheticContractLog.TRANSFER_SIGNATURE)
                        .topic1(AbstractSyntheticContractLog.entityIdToBytes(EntityId.of(sender.getId())))
                        .topic2(AbstractSyntheticContractLog.entityIdToBytes(EntityId.of(receiver.getId())))
                        .topic3(null)
                        .data(new byte[] {0})
                        .contractResult(contractResult))
                .get();

        entityListener.onContractLog(syntheticContractLog);

        var expectedBloom = new LogsBloomFilter();
        expectedBloom.insertAddress(existingAddress);
        expectedBloom.insertAddress(trim(contractEntity.getEvmAddress()));
        expectedBloom.insertTopic(AbstractSyntheticContractLog.TRANSFER_SIGNATURE);
        expectedBloom.insertTopic(trim(sender.getEvmAddress()));
        expectedBloom.insertTopic(trim(receiver.getEvmAddress()));

        transactionTemplate.executeWithoutResult(status -> {
            recordFileStreamListener.onEnd(recordFile);
        });

        assertThat(contractResult.getBloom()).hasSize(LogsBloomFilter.BYTE_SIZE);
        assertArrayEquals(expectedBloom.toArrayUnsafe(), contractResult.getBloom());
    }

    @Test
    void contractResultBloomSetWhenNoExistingBloom() {
        var sender = domainBuilder.entity().persist();
        var receiver = domainBuilder.entity().persist();
        var contractEntity = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.CONTRACT))
                .persist();

        var consensusTimestamp = domainBuilder.timestamp();

        var contractResult = domainBuilder
                .contractResult()
                .customize(cr -> cr.consensusTimestamp(consensusTimestamp)
                        .contractId(contractEntity.getId())
                        .payerAccountId(EntityId.of(domainBuilder.id()))
                        .bloom(null))
                .get();

        parserContext.add(contractResult, contractResult.getConsensusTimestamp());

        var recordFile = domainBuilder
                .recordFile()
                .customize(r -> r.sidecars(List.of()).logsBloom(null))
                .get();

        parserContext.add(recordFile);

        var markerBloom = new byte[] {1};
        var syntheticContractLog = domainBuilder
                .contractLog()
                .customize(cl -> cl.synthetic(true)
                        .bloom(markerBloom)
                        .consensusTimestamp(consensusTimestamp)
                        .contractId(EntityId.of(contractEntity.getId()))
                        .topic0(AbstractSyntheticContractLog.TRANSFER_SIGNATURE)
                        .topic1(AbstractSyntheticContractLog.entityIdToBytes(EntityId.of(sender.getId())))
                        .topic2(AbstractSyntheticContractLog.entityIdToBytes(EntityId.of(receiver.getId())))
                        .topic3(null)
                        .data(new byte[] {0})
                        .contractResult(contractResult))
                .get();

        entityListener.onContractLog(syntheticContractLog);

        var expectedBloom = new LogsBloomFilter();
        expectedBloom.insertAddress(trim(contractEntity.getEvmAddress()));
        expectedBloom.insertTopic(AbstractSyntheticContractLog.TRANSFER_SIGNATURE);
        expectedBloom.insertTopic(trim(sender.getEvmAddress()));
        expectedBloom.insertTopic(trim(receiver.getEvmAddress()));

        transactionTemplate.executeWithoutResult(status -> {
            recordFileStreamListener.onEnd(recordFile);
        });

        assertThat(contractResult.getBloom()).hasSize(LogsBloomFilter.BYTE_SIZE);
        assertArrayEquals(expectedBloom.toArrayUnsafe(), contractResult.getBloom());
    }

    @Test
    void contractResultBloomUnchangedWhenNoMatchingSyntheticLogs() {
        var contractEntity = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.CONTRACT))
                .persist();

        var existingAddress = domainBuilder.evmAddress();
        var existingBloomFilter = new LogsBloomFilter();
        existingBloomFilter.insertAddress(existingAddress);
        var existingBloom = existingBloomFilter.toArrayUnsafe();

        var consensusTimestamp = domainBuilder.timestamp();

        var contractResult = domainBuilder
                .contractResult()
                .customize(cr -> cr.consensusTimestamp(consensusTimestamp)
                        .contractId(contractEntity.getId())
                        .payerAccountId(EntityId.of(domainBuilder.id()))
                        .bloom(existingBloom))
                .get();

        parserContext.add(contractResult, contractResult.getConsensusTimestamp());

        var recordFile = domainBuilder
                .recordFile()
                .customize(r -> r.sidecars(List.of()).logsBloom(null))
                .get();

        parserContext.add(recordFile);

        transactionTemplate.executeWithoutResult(status -> {
            recordFileStreamListener.onEnd(recordFile);
        });

        assertArrayEquals(existingBloom, contractResult.getBloom());
    }
}
