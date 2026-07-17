// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.receipt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.common.util.LogsBloomFilter;
import org.hiero.mirror.importer.domain.EvmAddressMapping;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties.PersistProperties;
import org.hiero.mirror.importer.parser.record.entity.ParserContext;
import org.hiero.mirror.importer.parser.record.receipt.Receipt.ReceiptLog;
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

    private final ReceiptAssembler receiptAssembler = new ReceiptAssembler();
    private final ReceiptRootCalculator receiptRootCalculator = new ReceiptRootCalculator();
    private ReceiptRootService service;

    @BeforeEach
    void setup() {
        service = new ReceiptRootService(
                entityProperties, entityRepository, parserContext, receiptAssembler, receiptRootCalculator);
        lenient().when(entityProperties.getPersist()).thenReturn(persistProperties);
        lenient().when(persistProperties.isContractResults()).thenReturn(true);
        lenient().when(parserContext.get(RecordFile.class)).thenReturn(List.of());
        lenient().when(parserContext.get(EthereumTransaction.class)).thenReturn(List.of());
        lenient().when(parserContext.get(eq(Entity.class), any())).thenReturn(null);
        lenient().when(entityRepository.findEvmAddressesByIds(any())).thenReturn(List.of());
    }

    @Test
    void skippedWhenContractResultsDisabled() {
        when(persistProperties.isContractResults()).thenReturn(false);
        final var recordFile = new RecordFile();

        service.updateReceiptsRoot();

        assertThat(recordFile.getReceiptsRoot()).isNull();
    }

    @Test
    void emptyBlock() {
        final var recordFile = new RecordFile();
        when(parserContext.get(RecordFile.class)).thenReturn(List.of(recordFile));
        when(parserContext.get(ContractResult.class)).thenReturn(List.of());
        when(parserContext.get(ContractLog.class)).thenReturn(List.of());

        service.updateReceiptsRoot();

        assertThat(recordFile.getReceiptsRoot()).isEqualTo(new byte[32]);
    }

    @Test
    void noRecordFilesIgnored() {
        // ErrataMigration flushes with no record file in the parser context; must not throw.
        service.updateReceiptsRoot();
    }

    @Test
    void contractResultAndSyntheticOnlyReceipts() {
        final var bloom = fill((byte) 0x11, LogsBloomFilter.BYTE_SIZE);
        final var topicA = fill((byte) 0x01, 32);
        final var dataA = fill((byte) 0x02, 32);
        final var topicB0 = fill((byte) 0x03, 32);
        final var topicB1 = fill((byte) 0x04, 32);

        final var contractResult = ContractResult.builder()
                .consensusTimestamp(100L)
                .transactionIndex(0)
                .gasUsed(1000L)
                .transactionResult(ResponseCodeEnum.SUCCESS_VALUE)
                .bloom(bloom)
                .build();
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

        final var recordFile = new RecordFile();
        when(parserContext.get(RecordFile.class)).thenReturn(List.of(recordFile));
        when(parserContext.get(ContractResult.class)).thenReturn(List.of(contractResult));
        when(parserContext.get(ContractLog.class)).thenReturn(List.of(logA, logB));
        when(parserContext.get(EthereumTransaction.class)).thenReturn(List.of(ethereumTransaction));
        when(entityRepository.findEvmAddressesByIds(any()))
                .thenReturn(List.of(new EvmAddressMapping(ALIAS, ALIASED_CONTRACT.getId())));

        service.updateReceiptsRoot();

        final var expected = receiptRootCalculator.calculate(List.of(
                new Receipt(0, 2, true, null, 1000L, bloom, List.of(new ReceiptLog(ALIAS, List.of(topicA), dataA))),
                new Receipt(
                        1,
                        0,
                        true,
                        new byte[32],
                        0L,
                        syntheticBloom(DomainUtils.toEvmAddress(LONG_ZERO_CONTRACT), topicB0, topicB1),
                        List.of(new ReceiptLog(
                                DomainUtils.toEvmAddress(LONG_ZERO_CONTRACT),
                                List.of(topicB0, topicB1),
                                new byte[0])))));

        assertThat(recordFile.getReceiptsRoot()).isEqualTo(expected).hasSize(32);
    }

    @Test
    void childContractResultsExcluded() {
        final var bloom = fill((byte) 0x11, LogsBloomFilter.BYTE_SIZE);
        final var topLevel = ContractResult.builder()
                .consensusTimestamp(100L)
                .transactionIndex(0)
                .gasUsed(1000L)
                .transactionResult(ResponseCodeEnum.SUCCESS_VALUE)
                .bloom(bloom)
                .build();
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
        when(parserContext.get(RecordFile.class)).thenReturn(List.of(recordFile));
        when(parserContext.get(ContractResult.class)).thenReturn(List.of(topLevel, child));
        when(parserContext.get(ContractLog.class)).thenReturn(List.of());

        service.updateReceiptsRoot();

        assertThat(recordFile.getReceiptsRoot())
                .isEqualTo(receiptRootCalculator.calculate(
                        List.of(new Receipt(0, 0, true, null, 1000L, bloom, List.of()))));
    }

    @Test
    void batchPartitionsReceiptsByBlock() {
        final var bloom1 = fill((byte) 0x11, LogsBloomFilter.BYTE_SIZE);
        final var bloom2 = fill((byte) 0x22, LogsBloomFilter.BYTE_SIZE);

        // Two blocks in one batch with non-overlapping consensus ranges.
        final var block1 =
                RecordFile.builder().consensusStart(100L).consensusEnd(199L).build();
        final var block2 =
                RecordFile.builder().consensusStart(200L).consensusEnd(299L).build();

        final var result1 = ContractResult.builder()
                .consensusTimestamp(150L)
                .transactionIndex(0)
                .gasUsed(1000L)
                .transactionResult(ResponseCodeEnum.SUCCESS_VALUE)
                .bloom(bloom1)
                .build();
        final var result2 = ContractResult.builder()
                .consensusTimestamp(250L)
                .transactionIndex(0)
                .gasUsed(2000L)
                .transactionResult(ResponseCodeEnum.SUCCESS_VALUE)
                .bloom(bloom2)
                .build();

        when(parserContext.get(RecordFile.class)).thenReturn(List.of(block1, block2));
        when(parserContext.get(ContractResult.class)).thenReturn(List.of(result1, result2));
        when(parserContext.get(ContractLog.class)).thenReturn(List.of());

        service.updateReceiptsRoot();

        assertThat(block1.getReceiptsRoot())
                .isEqualTo(receiptRootCalculator.calculate(
                        List.of(new Receipt(0, 0, true, null, 1000L, bloom1, List.of()))));
        assertThat(block2.getReceiptsRoot())
                .isEqualTo(receiptRootCalculator.calculate(
                        List.of(new Receipt(0, 0, true, null, 2000L, bloom2, List.of()))));
    }

    private static byte[] syntheticBloom(final byte[] address, final byte[]... topics) {
        final var filter = new LogsBloomFilter();
        filter.insertAddress(address);
        for (final var topic : topics) {
            filter.insertTopic(topic);
        }
        return LogsBloomFilter.or(filter.toArrayUnsafe(), new byte[LogsBloomFilter.BYTE_SIZE]);
    }

    private static byte[] fill(final byte value, final int length) {
        final var array = new byte[length];
        java.util.Arrays.fill(array, value);
        return array;
    }
}
