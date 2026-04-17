// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.trim;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.primitives.Longs;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.RecordItem;
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
                .customize(cl -> cl.topic1(Longs.toByteArray(sender1.getNum()))
                        .topic2(Longs.toByteArray(receiver1.getNum()))
                        .synthetic(true))
                .get();

        entityListener.onContractLog(contractLog);

        assertArrayEquals(Longs.toByteArray(sender1.getNum()), contractLog.getTopic1());
        assertArrayEquals(Longs.toByteArray(receiver1.getNum()), contractLog.getTopic2());

        completeFileAndCommit();

        assertArrayEquals(trim(sender1.getEvmAddress()), contractLog.getTopic1());
        assertArrayEquals(trim(receiver1.getEvmAddress()), contractLog.getTopic2());
        assertThat(contractLogRepository.count()).isEqualTo(1);
        assertThat(contractLogRepository.findAll()).containsExactly(contractLog);
    }

    @Test
    void childContractExecutionWithAliasTopicsUsesParentTransactionHash() {
        byte[] senderEvm = Bytes.fromHexString("0xd838319b64e38ca2ed8b08c9324ebe4d37facecf")
                .toArrayUnsafe();
        byte[] receiverEvm = Bytes.fromHexString("0x2818870d1daa81b75e38cec44ceae51b4b684696")
                .toArrayUnsafe();
        byte[] contractEvm = Bytes.fromHexString("0xabcdef1234567890abcdef1234567890abcdef12")
                .toArrayUnsafe();
        byte[] parentTransactionHash = Bytes.fromHexString(
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
        byte[] topic1Before = AbstractSyntheticContractLog.entityIdToBytes(senderEntityId);
        byte[] topic2Before = AbstractSyntheticContractLog.entityIdToBytes(receiverEntityId);

        byte[] markerBloom = new byte[] {1};

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
        RecordFile recordFile =
                domainBuilder.recordFile().customize(r -> r.sidecars(List.of())).get();
        transactionTemplate.executeWithoutResult(status -> recordFileStreamListener.onEnd(recordFile));
    }

    @Test
    void recordFileBloomUnchangedWhenNoSyntheticLogs() {
        byte[] existingBloom = domainBuilder.bloomFilter();
        RecordFile recordFile = domainBuilder
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

        byte[] existingAddress = domainBuilder.evmAddress();
        var existingBloomFilter = new LogsBloomFilter();
        existingBloomFilter.insertAddress(existingAddress);
        byte[] existingBloom = existingBloomFilter.toArrayUnsafe();

        RecordFile recordFile = domainBuilder
                .recordFile()
                .customize(r -> r.sidecars(List.of()).logsBloom(existingBloom))
                .get();

        parserContext.add(recordFile);

        var syntheticLogBloom = new LogsBloomFilter();
        syntheticLogBloom.insertAddress(trim(contractEntity.getEvmAddress()));
        syntheticLogBloom.insertTopic(AbstractSyntheticContractLog.TRANSFER_SIGNATURE);
        syntheticLogBloom.insertTopic(trim(sender.getEvmAddress()));
        syntheticLogBloom.insertTopic(trim(receiver.getEvmAddress()));

        RecordItem recordItem = mock(RecordItem.class);
        when(recordItem.getMergedSyntheticContractLogsBloom()).thenReturn(syntheticLogBloom.toArrayUnsafe());

        byte[] markerBloom = new byte[] {1};
        var syntheticContractLog = domainBuilder
                .contractLog()
                .customize(cl -> cl.synthetic(true)
                        .bloom(markerBloom)
                        .contractId(EntityId.of(contractEntity.getId()))
                        .topic0(AbstractSyntheticContractLog.TRANSFER_SIGNATURE)
                        .topic1(AbstractSyntheticContractLog.entityIdToBytes(EntityId.of(sender.getId())))
                        .topic2(AbstractSyntheticContractLog.entityIdToBytes(EntityId.of(receiver.getId())))
                        .topic3(null)
                        .data(new byte[] {0})
                        .recordItem(recordItem))
                .get();

        entityListener.onContractLog(syntheticContractLog);

        var expectedBloom = new LogsBloomFilter();
        expectedBloom.insertAddress(existingAddress); // existing bloom
        expectedBloom.insertAddress(trim(contractEntity.getEvmAddress())); // synthetic log's contract address
        expectedBloom.insertTopic(AbstractSyntheticContractLog.TRANSFER_SIGNATURE);
        expectedBloom.insertTopic(trim(sender.getEvmAddress())); // topic1 after EVM address lookup
        expectedBloom.insertTopic(trim(receiver.getEvmAddress())); // topic2 after EVM address lookup

        transactionTemplate.executeWithoutResult(status -> {
            recordFileStreamListener.onEnd(recordFile);
            parserContext.clear();
        });

        byte[] resultBloom = recordFile.getLogsBloom();
        assertArrayEquals(expectedBloom.toArrayUnsafe(), resultBloom);
    }

    @Test
    void recordFileBloomSetWhenOnlySyntheticBloomAdded() {
        var sender = domainBuilder.entity().persist();
        var receiver = domainBuilder.entity().persist();
        var contractEntity = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.CONTRACT))
                .persist();

        RecordFile recordFile = domainBuilder
                .recordFile()
                .customize(r -> r.sidecars(List.of()).logsBloom(null))
                .get();

        parserContext.add(recordFile);

        var syntheticLogBloom = new LogsBloomFilter();
        syntheticLogBloom.insertAddress(trim(contractEntity.getEvmAddress()));
        syntheticLogBloom.insertTopic(AbstractSyntheticContractLog.TRANSFER_SIGNATURE);
        syntheticLogBloom.insertTopic(trim(sender.getEvmAddress()));
        syntheticLogBloom.insertTopic(trim(receiver.getEvmAddress()));

        RecordItem recordItem = mock(RecordItem.class);
        when(recordItem.getMergedSyntheticContractLogsBloom()).thenReturn(syntheticLogBloom.toArrayUnsafe());

        byte[] markerBloom = new byte[] {1};
        var syntheticContractLog = domainBuilder
                .contractLog()
                .customize(cl -> cl.synthetic(true)
                        .bloom(markerBloom)
                        .contractId(EntityId.of(contractEntity.getId()))
                        .topic0(AbstractSyntheticContractLog.TRANSFER_SIGNATURE)
                        .topic1(AbstractSyntheticContractLog.entityIdToBytes(EntityId.of(sender.getId())))
                        .topic2(AbstractSyntheticContractLog.entityIdToBytes(EntityId.of(receiver.getId())))
                        .topic3(null)
                        .data(new byte[] {0})
                        .recordItem(recordItem))
                .get();

        entityListener.onContractLog(syntheticContractLog);

        var expectedBloom = new LogsBloomFilter();
        expectedBloom.insertAddress(trim(contractEntity.getEvmAddress()));
        expectedBloom.insertTopic(AbstractSyntheticContractLog.TRANSFER_SIGNATURE);
        expectedBloom.insertTopic(trim(sender.getEvmAddress()));
        expectedBloom.insertTopic(trim(receiver.getEvmAddress()));

        transactionTemplate.executeWithoutResult(status -> {
            recordFileStreamListener.onEnd(recordFile);
            parserContext.clear();
        });

        byte[] resultBloom = recordFile.getLogsBloom();
        assertArrayEquals(expectedBloom.toArrayUnsafe(), resultBloom);
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

        byte[] existingAddress = domainBuilder.evmAddress();
        var existingBloomFilter = new LogsBloomFilter();
        existingBloomFilter.insertAddress(existingAddress);
        byte[] existingBloom = existingBloomFilter.toArrayUnsafe();

        RecordFile recordFile = domainBuilder
                .recordFile()
                .customize(r -> r.sidecars(List.of()).logsBloom(existingBloom))
                .get();

        parserContext.add(recordFile);

        var combinedSyntheticBloom = new LogsBloomFilter();
        combinedSyntheticBloom.insertAddress(trim(contractEntity.getEvmAddress()));
        combinedSyntheticBloom.insertTopic(AbstractSyntheticContractLog.TRANSFER_SIGNATURE);
        combinedSyntheticBloom.insertTopic(trim(sender1.getEvmAddress()));
        combinedSyntheticBloom.insertTopic(trim(receiver1.getEvmAddress()));
        combinedSyntheticBloom.insertTopic(trim(sender2.getEvmAddress()));
        combinedSyntheticBloom.insertTopic(trim(receiver2.getEvmAddress()));

        RecordItem recordItem = mock(RecordItem.class);
        when(recordItem.getMergedSyntheticContractLogsBloom()).thenReturn(combinedSyntheticBloom.toArrayUnsafe());

        byte[] markerBloom = new byte[] {1};

        var syntheticContractLog1 = domainBuilder
                .contractLog()
                .customize(cl -> cl.synthetic(true)
                        .bloom(markerBloom)
                        .contractId(EntityId.of(contractEntity.getId()))
                        .topic0(AbstractSyntheticContractLog.TRANSFER_SIGNATURE)
                        .topic1(AbstractSyntheticContractLog.entityIdToBytes(EntityId.of(sender1.getId())))
                        .topic2(AbstractSyntheticContractLog.entityIdToBytes(EntityId.of(receiver1.getId())))
                        .topic3(null)
                        .data(new byte[] {0})
                        .recordItem(recordItem))
                .get();

        var syntheticContractLog2 = domainBuilder
                .contractLog()
                .customize(cl -> cl.synthetic(true)
                        .bloom(markerBloom)
                        .contractId(EntityId.of(contractEntity.getId()))
                        .topic0(AbstractSyntheticContractLog.TRANSFER_SIGNATURE)
                        .topic1(AbstractSyntheticContractLog.entityIdToBytes(EntityId.of(sender2.getId())))
                        .topic2(AbstractSyntheticContractLog.entityIdToBytes(EntityId.of(receiver2.getId())))
                        .topic3(null)
                        .data(new byte[] {0})
                        .recordItem(recordItem))
                .get();

        entityListener.onContractLog(syntheticContractLog1);
        entityListener.onContractLog(syntheticContractLog2);

        var expectedBloom = new LogsBloomFilter();
        expectedBloom.insertAddress(existingAddress); // existing bloom
        expectedBloom.insertAddress(trim(contractEntity.getEvmAddress()));
        expectedBloom.insertTopic(AbstractSyntheticContractLog.TRANSFER_SIGNATURE);
        expectedBloom.insertTopic(trim(sender1.getEvmAddress()));
        expectedBloom.insertTopic(trim(receiver1.getEvmAddress()));
        expectedBloom.insertTopic(trim(sender2.getEvmAddress()));
        expectedBloom.insertTopic(trim(receiver2.getEvmAddress()));

        transactionTemplate.executeWithoutResult(status -> {
            recordFileStreamListener.onEnd(recordFile);
            parserContext.clear();
        });

        byte[] resultBloom = recordFile.getLogsBloom();
        assertArrayEquals(expectedBloom.toArrayUnsafe(), resultBloom);
    }

    @Test
    void contractResultBloomMergesSyntheticBloom() {
        var sender = domainBuilder.entity().persist();
        var receiver = domainBuilder.entity().persist();
        var contractEntity = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.CONTRACT))
                .persist();

        byte[] existingAddress = domainBuilder.evmAddress();
        var existingBloomFilter = new LogsBloomFilter();
        existingBloomFilter.insertAddress(existingAddress);
        byte[] existingBloom = existingBloomFilter.toArrayUnsafe();

        long consensusTimestamp = domainBuilder.timestamp();

        ContractResult contractResult = domainBuilder
                .contractResult()
                .customize(cr -> cr.consensusTimestamp(consensusTimestamp)
                        .contractId(contractEntity.getId())
                        .payerAccountId(EntityId.of(domainBuilder.id()))
                        .bloom(existingBloom))
                .get();

        parserContext.add(contractResult, contractResult.getConsensusTimestamp());

        RecordFile recordFile = domainBuilder
                .recordFile()
                .customize(r -> r.sidecars(List.of()).logsBloom(null))
                .get();

        parserContext.add(recordFile);

        RecordItem recordItem = mock(RecordItem.class);
        when(recordItem.getConsensusTimestamp()).thenReturn(consensusTimestamp);

        byte[] markerBloom = new byte[] {1};
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
                        .recordItem(recordItem))
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

        long consensusTimestamp = domainBuilder.timestamp();

        ContractResult contractResult = domainBuilder
                .contractResult()
                .customize(cr -> cr.consensusTimestamp(consensusTimestamp)
                        .contractId(contractEntity.getId())
                        .payerAccountId(EntityId.of(domainBuilder.id()))
                        .bloom(null))
                .get();

        parserContext.add(contractResult, contractResult.getConsensusTimestamp());

        RecordFile recordFile = domainBuilder
                .recordFile()
                .customize(r -> r.sidecars(List.of()).logsBloom(null))
                .get();

        parserContext.add(recordFile);

        RecordItem recordItem = mock(RecordItem.class);
        when(recordItem.getConsensusTimestamp()).thenReturn(consensusTimestamp);

        byte[] markerBloom = new byte[] {1};
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
                        .recordItem(recordItem))
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

        byte[] existingAddress = domainBuilder.evmAddress();
        var existingBloomFilter = new LogsBloomFilter();
        existingBloomFilter.insertAddress(existingAddress);
        byte[] existingBloom = existingBloomFilter.toArrayUnsafe();

        long consensusTimestamp = domainBuilder.timestamp();

        ContractResult contractResult = domainBuilder
                .contractResult()
                .customize(cr -> cr.consensusTimestamp(consensusTimestamp)
                        .contractId(contractEntity.getId())
                        .payerAccountId(EntityId.of(domainBuilder.id()))
                        .bloom(existingBloom))
                .get();

        parserContext.add(contractResult, contractResult.getConsensusTimestamp());

        RecordFile recordFile = domainBuilder
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
