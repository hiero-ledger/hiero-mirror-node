// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.trim;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import com.google.common.primitives.Longs;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.util.LogsBloomFilter;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.parser.record.RecordStreamFileListener;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.repository.ContractLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

@RequiredArgsConstructor
public class SyntheticLogListenerIntegrationTest extends ImporterIntegrationTest {
    private final SyntheticLogListener syntheticLogListener;
    private final RecordStreamFileListener recordFileStreamListener;
    private final DomainBuilder domainBuilder;
    private final TransactionTemplate transactionTemplate;
    private final ContractLogRepository contractLogRepository;
    private final EntityListener entityListener;

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
}
