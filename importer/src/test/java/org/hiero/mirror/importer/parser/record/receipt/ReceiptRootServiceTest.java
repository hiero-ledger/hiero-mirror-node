// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.receipt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import java.util.Map;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.util.LogsBloomFilter;
import org.hiero.mirror.importer.domain.EvmAddressMapping;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties.PersistProperties;
import org.hiero.mirror.importer.parser.record.entity.ParserContext;
import org.hiero.mirror.importer.repository.EntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class ReceiptRootServiceTest {

    private static final EntityId ALIASED_CONTRACT = EntityId.of(0, 0, 500);
    private static final EntityId LONG_ZERO_CONTRACT = EntityId.of(0, 0, 600);
    private static final byte[] ALIAS = fill((byte) 0xAB, 20);

    @Mock
    private EntityProperties entityProperties;

    @Mock
    private PersistProperties persistProperties;

    @Mock
    private EntityRepository entityRepository;

    @Mock
    private ParserContext parserContext;

    private ReceiptRootService service;

    @BeforeEach
    void setup() {
        service = new ReceiptRootService(entityProperties, entityRepository, parserContext);
        lenient().when(entityProperties.getPersist()).thenReturn(persistProperties);
        lenient().when(persistProperties.isContractResults()).thenReturn(true);
        lenient().when(parserContext.get(eq(Entity.class), any())).thenReturn(null);
        lenient().when(entityRepository.findEvmAddressesByIds(any())).thenReturn(List.of());
    }

    @Test
    void skippedWhenContractResultsDisabled() {
        when(persistProperties.isContractResults()).thenReturn(false);
        final var recordFile = new RecordFile();

        assertThat(service.isEnabled()).isFalse();
        service.complete(recordFile);
        service.updateReceiptsRoots();

        assertThat(recordFile.getReceiptsRoot()).isNull();
    }

    @Test
    void emptyBlock() {
        final var recordFile = new RecordFile();

        service.complete(recordFile);
        service.updateReceiptsRoots();

        assertThat(recordFile.getReceiptsRoot()).isEqualTo(new byte[32]);
    }

    @Test
    void noCompletedRecordFilesIgnored() {
        // ErrataMigration flushes without parsing a record file; must not throw and must discard streamed state
        service.onContractResult(contractResult(100L, 0, 1000L));
        service.updateReceiptsRoots();

        final var recordFile = new RecordFile();
        service.complete(recordFile);
        service.updateReceiptsRoots();

        // the stray contract result streamed before the flush must not leak into the next block
        assertThat(recordFile.getReceiptsRoot()).isEqualTo(new byte[32]);
    }

    @Test
    void contractResultAndSyntheticOnlyReceipts() {
        final var bloom = fill((byte) 0x11, LogsBloomFilter.BYTE_SIZE);
        final var topicA = fill((byte) 0x01, 32);
        final var dataA = fill((byte) 0x02, 32);
        final var topicB0 = fill((byte) 0x03, 32);
        final var topicB1 = fill((byte) 0x04, 32);

        final var contractResult = contractResult(100L, 0, 1000L, bloom);
        final var logA = ContractLog.builder()
                .consensusTimestamp(100L)
                .index(0)
                .contractId(ALIASED_CONTRACT)
                .topic0(topicA)
                .data(dataA)
                .transactionIndex(0)
                .build();
        final var ethereumTransaction =
                EthereumTransaction.builder().consensusTimestamp(100L).type(2).build();

        // Synthetic-only transaction: logs but no contract result.
        final var logB = ContractLog.builder()
                .consensusTimestamp(200L)
                .index(0)
                .contractId(LONG_ZERO_CONTRACT)
                .topic0(topicB0)
                .topic1(topicB1)
                .data(new byte[0])
                .transactionIndex(1)
                .build();

        when(entityRepository.findEvmAddressesByIds(any()))
                .thenReturn(List.of(new EvmAddressMapping(ALIAS, ALIASED_CONTRACT.getId())));

        final var recordFile = new RecordFile();
        service.onContractResult(contractResult);
        service.onContractLog(logA);
        service.onEthereumTransaction(ethereumTransaction);
        service.onContractLog(logB);
        service.complete(recordFile);
        service.updateReceiptsRoots();

        final var expectedRoot = new ReceiptRoot();
        expectedRoot.add(contractResult);
        expectedRoot.add(logA);
        expectedRoot.add(logB);
        final var expected = expectedRoot.getRootHash(Map.of(100L, 2), Map.of(ALIASED_CONTRACT.getId(), ALIAS));

        assertThat(recordFile.getReceiptsRoot()).isEqualTo(expected).hasSize(32);
    }

    @Test
    void childContractResultsExcluded() {
        final var topLevel = contractResult(100L, 0, 1000L);
        // Child (internal) contract result; must not become a receipt and its gas must not accumulate
        final var child = ContractResult.builder()
                .consensusTimestamp(101L)
                .transactionIndex(1)
                .transactionNonce(1)
                .gasUsed(500L)
                .transactionResult(ResponseCodeEnum.SUCCESS_VALUE)
                .bloom(fill((byte) 0x22, LogsBloomFilter.BYTE_SIZE))
                .build();

        final var recordFile = new RecordFile();
        service.onContractResult(topLevel);
        service.onContractResult(child);
        service.complete(recordFile);
        service.updateReceiptsRoots();

        final var expectedRoot = new ReceiptRoot();
        expectedRoot.add(topLevel);

        assertThat(recordFile.getReceiptsRoot()).isEqualTo(expectedRoot.getRootHash(Map.of(), Map.of()));
    }

    @Test
    void batchScopesReceiptsPerRecordFile() {
        // Two record files parsed in one batch: each file's streamed receipts must only affect its own root even
        // though the flush happens once at the end of the batch
        final var result1 = contractResult(150L, 0, 1000L);
        final var result2 = contractResult(250L, 0, 2000L);

        final var block1 = new RecordFile();
        final var block2 = new RecordFile();
        service.onContractResult(result1);
        service.complete(block1);
        service.onContractResult(result2);
        service.complete(block2);
        service.updateReceiptsRoots();

        final var expected1 = new ReceiptRoot();
        expected1.add(result1);
        final var expected2 = new ReceiptRoot();
        expected2.add(result2);

        assertThat(block1.getReceiptsRoot()).isEqualTo(expected1.getRootHash(Map.of(), Map.of()));
        assertThat(block2.getReceiptsRoot()).isEqualTo(expected2.getRootHash(Map.of(), Map.of()));
    }

    private static ContractResult contractResult(final long consensusTimestamp, final int index, final long gasUsed) {
        return contractResult(consensusTimestamp, index, gasUsed, fill((byte) 0x11, LogsBloomFilter.BYTE_SIZE));
    }

    private static ContractResult contractResult(
            final long consensusTimestamp, final int index, final long gasUsed, final byte[] bloom) {
        return ContractResult.builder()
                .consensusTimestamp(consensusTimestamp)
                .transactionIndex(index)
                .gasUsed(gasUsed)
                .transactionResult(ResponseCodeEnum.SUCCESS_VALUE)
                .bloom(bloom)
                .build();
    }

    private static byte[] fill(final byte value, final int length) {
        final var array = new byte[length];
        java.util.Arrays.fill(array, value);
        return array;
    }
}
