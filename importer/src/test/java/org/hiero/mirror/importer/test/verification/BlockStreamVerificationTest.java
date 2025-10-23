// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.test.verification;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimaps;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.GeneratedMessage;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.CustomLog;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.CommonConfiguration;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.SidecarFile;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.addressbook.ConsensusNode;
import org.hiero.mirror.importer.addressbook.ConsensusNodeService;
import org.hiero.mirror.importer.domain.StreamFilename;
import org.hiero.mirror.importer.downloader.provider.StreamFileProvider;
import org.hiero.mirror.importer.downloader.record.RecordDownloaderProperties;
import org.hiero.mirror.importer.exception.HashMismatchException;
import org.hiero.mirror.importer.reader.record.RecordFileReader;
import org.hiero.mirror.importer.reader.record.sidecar.SidecarFileReader;
import org.hiero.mirror.importer.repository.ContractActionRepository;
import org.hiero.mirror.importer.repository.ContractRepository;
import org.hiero.mirror.importer.repository.ContractResultRepository;
import org.hiero.mirror.importer.repository.ContractStateChangeRepository;
import org.hiero.mirror.importer.repository.EthereumTransactionRepository;
import org.hiero.mirror.importer.repository.TransactionRepository;
import org.hiero.mirror.importer.util.Utility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@CustomLog
@DisableRepeatableSqlMigration
@Import(CommonConfiguration.class)
@RequiredArgsConstructor
@SpringBootTest
@SuppressWarnings("deprecation")
@TestPropertySource(
        properties = {"hiero.mirror.importer.downloader.bucketName=", "spring.flyway.baselineVersion=2.999.999"})
class BlockStreamVerificationTest {

    private static final Map<String, String> BASE_URLS = Map.of(
            ImporterProperties.HederaNetwork.MAINNET, baseUrlFor(ImporterProperties.HederaNetwork.MAINNET),
            ImporterProperties.HederaNetwork.PREVIEWNET, baseUrlFor(ImporterProperties.HederaNetwork.PREVIEWNET),
            ImporterProperties.HederaNetwork.TESTNET, baseUrlFor(ImporterProperties.HederaNetwork.TESTNET));
    private static final String BLOCKS_URI = "/blocks?timestamp=gte:{timestamp}&limit=25&order=asc";
    // 4-byte shard + 8-byte realm, all 0s
    private static final ByteString DEFAULT_LONG_FORM_ADDRESS_PREFIX = ByteString.copyFrom(new byte[12]);
    private static final String VALUE_DIFF_TEMPLATE = "%s: actual value - %s, expected value - %s";

    private final BlockStreamVerificationProperties properties;
    private final ConsensusNodeService consensusNodeService;
    private final ContractActionRepository contractActionRepository;
    private final ContractRepository contractRepository;
    private final ContractResultRepository contractResultRepository;
    private final ContractStateChangeRepository contractStateChangeRepository;
    private final EthereumTransactionRepository ethereumTransactionRepository;
    private final ImporterProperties importerProperties;
    private final RestClient restClient;
    private final List<String> recordFiles = new ArrayList<>();
    private final RecordDownloaderProperties recordDownloaderProperties;
    private final RecordFileReader recordFileReader;
    private final SidecarFileReader sidecarFileReader;
    private final Stats stats = new Stats();
    private final StreamFileProvider streamFileProvider;
    private final TransactionRepository transactionRepository;

    private RecordFile current;
    private int recordItemIndex = 0;
    private List<Consumer<RecordItemContext>> recordItemPatchers;

    @BeforeEach
    void setup() {
        recordItemPatchers = List.of(this::patchTransactionReceipt, this::patchContractFunctionResult);
    }

    @Test
    void verify() {
        final long endConsensusTimestamp = properties.getEndConsensusTimestamp();
        long lastConsensusTimestamp = properties.getStartConsensusTimestamp() - 1;
        final int limit = 100;
        for (; ; ) {
            var transactions =
                    transactionRepository.findInTimestampRange(endConsensusTimestamp, lastConsensusTimestamp, limit);
            if (transactions.isEmpty()) {
                break;
            }

            transactions.forEach(this::verifyTransaction);
            lastConsensusTimestamp = transactions.getLast().getConsensusTimestamp();
        }

        log.info("Result: {}", stats);
    }

    private static String baseUrlFor(String network) {
        return "https://%s.mirrornode.hedera.com/api/v1".formatted(network);
    }

    private static List<String> compareFiledByField(
            GeneratedMessage actual, GeneratedMessage expected, String fieldPath) {
        if (Objects.equals(actual, expected)) {
            return Collections.emptyList();
        }

        var diffs = new ArrayList<String>();
        var fieldDescriptors = actual.getDescriptorForType().getFields();

        for (var fieldDescriptor : fieldDescriptors) {
            var actualField = actual.getField(fieldDescriptor);
            var expectedField = expected.getField(fieldDescriptor);

            if (!Objects.equals(actualField, expectedField)) {
                var fullPath = StringUtils.joinWith(".", fieldPath, fieldDescriptor.getName());
                boolean isComplexType = fieldDescriptor.getType() == Descriptors.FieldDescriptor.Type.MESSAGE;

                if (fieldDescriptor.isRepeated()) {
                    var actualList = (List<?>) actualField;
                    var expectedList = (List<?>) expectedField;
                    int size = Math.min(actualList.size(), expectedList.size());

                    if (actualList.size() != expectedList.size()) {
                        diffs.add("%s: actual list size - %d, expected list size - %d"
                                .formatted(fullPath, actualList.size(), expectedList.size()));
                    }

                    for (int i = 0; i < size; i++) {
                        var actualElement = actualList.get(i);
                        var expectedElement = expectedList.get(i);
                        var elementPath = "%s[%d]".formatted(fullPath, i);

                        if (isComplexType) {
                            diffs.addAll(compareFiledByField(
                                    (GeneratedMessage) actualElement, (GeneratedMessage) expectedElement, elementPath));
                        } else if (!Objects.equals(actualElement, expectedElement)) {
                            // simple type in a repeated field
                            diffs.add(VALUE_DIFF_TEMPLATE.formatted(elementPath, actualElement, expectedElement));
                        }
                    }
                } else if (isComplexType) {
                    diffs.addAll(compareFiledByField(
                            (GeneratedMessage) actualField, (GeneratedMessage) expectedField, fullPath));
                } else {
                    // simple type
                    diffs.add(VALUE_DIFF_TEMPLATE.formatted(fullPath, actualField, expectedField));
                }
            }
        }

        return diffs;
    }

    private static String toTimestampString(long timestamp) {
        String str = StringUtils.leftPad(Long.toString(timestamp), 10, '0');
        int fractionIndex = str.length() - 9;
        return "%s.%s".formatted(str.substring(0, fractionIndex), str.substring(fractionIndex));
    }

    private List<String> compareSidecarRecords(RecordItem expectedRecordItem, Transaction transaction) {
        //        expectedRecordItem.getSidecarRecords();
        return Collections.emptyList();
    }

    private List<String> compareTransactionRecord(RecordItem expectedRecordItem, Transaction transaction) {
        var actualRecord = patch(transaction);
        var expectedRecord = patch(expectedRecordItem);
        return compareFiledByField(actualRecord, expectedRecord, "TransactionRecord");
    }

    private RecordFile getRecordFile(String filename) {
        log.info("Downloading record file {}...", filename);
        RecordFile recordFile = null;
        for (var node : consensusNodeService.getNodes()) {
            try {
                var filePath = "recordstreams/record%s/%s"
                        .formatted(node.getNodeAccountId().toString(), filename);
                var streamFilename = StreamFilename.from(filePath);
                var streamFile = streamFileProvider.get(node, streamFilename).block();
                recordFile = recordFileReader.read(streamFile);
                if (recordFile.getSidecarCount() > 0) {
                    getSidecars(streamFilename, recordFile, node);
                }

            } catch (Exception e) {
                recordFile = null;
            }
        }

        if (recordFile == null) {
            throw new IllegalStateException("Failed to get file %s from all nodes".formatted(filename));
        }

        log.info("Downloaded record file {}, index {}", filename, recordFile.getIndex());
        return recordFile;
    }

    private Mono<SidecarFile> getSidecar(ConsensusNode node, StreamFilename recordFilename, SidecarFile sidecar) {
        var sidecarFilename = StreamFilename.from(recordFilename, sidecar.getName());
        return streamFileProvider.get(node, sidecarFilename).map(streamFileData -> {
            sidecarFileReader.read(sidecar, streamFileData);

            if (!Arrays.equals(sidecar.getHash(), sidecar.getActualHash())) {
                throw new HashMismatchException(
                        sidecar.getName(), sidecar.getHash(), sidecar.getActualHash(), "sidecar");
            }

            if (recordDownloaderProperties.isWriteFiles()) {
                var streamPath = importerProperties.getArchiveDestinationFolderPath(streamFileData);
                Utility.archiveFile(streamFileData.getFilePath(), sidecar.getBytes(), streamPath);
            }

            sidecar.setBytes(null);
            return sidecar;
        });
    }

    private void getSidecars(StreamFilename recordFilename, RecordFile recordFile, ConsensusNode node) {
        var records = Flux.fromIterable(recordFile.getSidecars())
                .flatMap(sidecar -> getSidecar(node, recordFilename, sidecar))
                .flatMapIterable(SidecarFile::getRecords)
                .collect(Multimaps.toMultimap(
                        TransactionSidecarRecord::getConsensusTimestamp,
                        Function.identity(),
                        ArrayListMultimap::create))
                .block();

        if (records == null) {
            return;
        }

        recordFile.getItems().forEach(recordItem -> {
            var timestamp = recordItem.getTransactionRecord().getConsensusTimestamp();
            if (records.containsKey(timestamp)) {
                recordItem.setSidecarRecords(records.get(timestamp));
            }
        });
    }

    private RecordItem getExpectedRecordItem(long consensusTimestamp) {
        if (current == null
                || current.getConsensusStart() > consensusTimestamp
                || current.getConsensusEnd() < consensusTimestamp) {
            if (recordFiles.isEmpty()) {
                var response = restClient
                        .get()
                        .uri(BLOCKS_URI, toTimestampString(consensusTimestamp))
                        .retrieve()
                        .body(BlocksResponse.class);
                response.getBlocks().forEach(block -> recordFiles.add(block.getName()));
            }

            recordItemIndex = 0; // reset index
            current = getRecordFile(recordFiles.removeFirst());
        }

        while (recordItemIndex < current.getCount()) {
            var recordItem = current.getItems().get(recordItemIndex); // peek
            long actualConsensusTimestamp = recordItem.getConsensusTimestamp();
            if (consensusTimestamp == actualConsensusTimestamp) {
                recordItemIndex++;
                return recordItem;
            }

            log.error(
                    "Consensus timestamp mismatch: actual {}, expected {}",
                    actualConsensusTimestamp,
                    consensusTimestamp);
            if (actualConsensusTimestamp > consensusTimestamp) {
                return null;
            }

            recordItemIndex++;
        }

        return null;
    }

    @SneakyThrows
    private TransactionRecord patch(Transaction transaction) {
        var builder = TransactionRecord.parseFrom(transaction.getTransactionRecordBytes()).toBuilder();
        if (transaction.getType() == TransactionType.ETHEREUMTRANSACTION.getProtoId()) {
            ethereumTransactionRepository
                    .findById(transaction.getConsensusTimestamp())
                    .ifPresent(e -> builder.setEthereumHash(DomainUtils.fromBytes(e.getHash())));
        }

        if ((builder.hasContractCallResult() || builder.hasContractCreateResult())
                && transaction.getType() != TransactionType.CONTRACTCALL.getProtoId()
                && transaction.getType() != TransactionType.CONTRACTCREATEINSTANCE.getProtoId()) {
            contractResultRepository
                    .findById(transaction.getConsensusTimestamp())
                    .ifPresent(contractResult -> {
                        var contractResultBuilder = builder.hasContractCallResult()
                                ? builder.getContractCallResultBuilder()
                                : builder.getContractCreateResultBuilder();
                        contractResultBuilder.setAmount(contractResult.getAmount());
                        contractResultBuilder.setGas(contractResult.getGasLimit());
                        contractResultBuilder.setFunctionParameters(
                                DomainUtils.fromBytes(contractResult.getFunctionParameters()));
                    });
        }

        return builder.build();
    }

    private TransactionRecord patch(RecordItem recordItem) {
        var context = new RecordItemContext(recordItem);
        recordItemPatchers.forEach(p -> p.accept(context));
        return context.builder().build();
    }

    private void patchContractFunctionResult(RecordItemContext context) {
        var builder = context.builder();
        var contractResultBuilder = builder.hasContractCallResult()
                ? builder.getContractCallResultBuilder()
                : (builder.hasContractCreateResult() ? builder.getContractCreateResultBuilder() : null);
        if (contractResultBuilder == null) {
            return;
        }

        contractResultBuilder.clearCreatedContractIDs();
        if (contractResultBuilder.hasEvmAddress()) {
            var evmAddress = contractResultBuilder.getEvmAddress().getValue();
            if (evmAddress
                    .substring(0, Math.min(evmAddress.size(), DEFAULT_LONG_FORM_ADDRESS_PREFIX.size()))
                    .equals(DEFAULT_LONG_FORM_ADDRESS_PREFIX)) {
                // clear long-form address set as evm_address in ContractFunctionResult
                contractResultBuilder.clearEvmAddress();
            }
        }
    }

    private void patchTransactionReceipt(RecordItemContext context) {
        var receiptBuilder = context.builder().getReceiptBuilder();
        receiptBuilder.clearExchangeRate();

        if (context.recordItem().getTransactionType() == TransactionType.SCHEDULEDELETE.getProtoId()) {
            receiptBuilder.clearScheduleID();
        }
    }

    private void verifyTransaction(Transaction transaction) {
        log.info(
                "Verifying {} transaction at {}",
                TransactionType.of(transaction.getType()),
                transaction.getConsensusTimestamp());

        var expectedRecordItem = getExpectedRecordItem(transaction.getConsensusTimestamp());
        if (expectedRecordItem == null) {
            return;
        }

        var diffs = ListUtils.union(
                compareTransactionRecord(expectedRecordItem, transaction),
                compareSidecarRecords(expectedRecordItem, transaction));
        boolean successful = diffs.isEmpty();
        log.info(
                "Transaction record {} at {}",
                diffs.isEmpty() ? "match" : "mismatch",
                transaction.getConsensusTimestamp());
        if (!successful) {
            log.info("Diffs - {}", StringUtils.join(diffs, "\n"));
        }
        stats.record(successful);
    }

    @TestConfiguration
    static class BlockStreamTestConfiguration {

        @Bean
        DomainBuilder domainBuilder(
                CommonProperties commonProperties,
                EntityManager entityManager,
                TransactionOperations transactionOperations) {
            return new DomainBuilder(commonProperties, entityManager, transactionOperations);
        }

        @Bean
        RestClient restClient(ImporterProperties importerProperties, RestClient.Builder restClientBuilder) {
            return restClientBuilder
                    .baseUrl(BASE_URLS.get(importerProperties.getNetwork()))
                    .build();
        }
    }

    record RecordItemContext(RecordItem recordItem, TransactionRecord.Builder builder) {
        RecordItemContext(RecordItem recordItem) {
            this(recordItem, recordItem.getTransactionRecord().toBuilder());
        }
    }

    @Data
    static class Stats {

        private int failed;
        private int total;
        private int success;

        void record(boolean successful) {
            total++;
            if (successful) {
                success++;
            } else {
                failed++;
            }
        }
    }
}
