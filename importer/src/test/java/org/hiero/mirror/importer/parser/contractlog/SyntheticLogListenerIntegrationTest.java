// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.parser.contractlog.AbstractSyntheticContractLog.TRANSFER_SIGNATURE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import com.google.common.primitives.Longs;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.transaction.RecordFile;
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

        assertArrayEquals(contractLog.getTopic1(), Longs.toByteArray(sender1.getNum()));
        assertArrayEquals(contractLog.getTopic2(), Longs.toByteArray(receiver1.getNum()));

        completeFileAndCommit();

        assertArrayEquals(contractLog.getTopic1(), Longs.toByteArray(sender1.getNum()));
        assertArrayEquals(contractLog.getTopic2(), Longs.toByteArray(receiver1.getNum()));

        assertThat(contractLogRepository.count()).isEqualTo(1);
        assertThat(contractLogRepository.findAll()).containsExactly(contractLog);
    }

    @Test
    void transferContractLog() {
        var sender1 = domainBuilder.entity().persist();
        var receiver1 = domainBuilder.entity().persist();

        var contractLog = domainBuilder
                .contractLog()
                .customize(cl -> cl.topic0(TRANSFER_SIGNATURE)
                        .topic1(Longs.toByteArray(sender1.getNum()))
                        .topic2(Longs.toByteArray(receiver1.getNum())))
                .get();

        entityListener.onContractLog(contractLog);

        assertArrayEquals(contractLog.getTopic1(), Longs.toByteArray(sender1.getNum()));
        assertArrayEquals(contractLog.getTopic2(), Longs.toByteArray(receiver1.getNum()));

        completeFileAndCommit();

        assertArrayEquals(contractLog.getTopic1(), sender1.getEvmAddress());
        assertArrayEquals(contractLog.getTopic2(), receiver1.getEvmAddress());

        assertThat(contractLogRepository.count()).isEqualTo(1);
        assertThat(contractLogRepository.findAll()).containsExactly(contractLog);
    }

    private void completeFileAndCommit() {
        RecordFile recordFile =
                domainBuilder.recordFile().customize(r -> r.sidecars(List.of())).get();
        transactionTemplate.executeWithoutResult(status -> recordFileStreamListener.onEnd(recordFile));
    }
}
