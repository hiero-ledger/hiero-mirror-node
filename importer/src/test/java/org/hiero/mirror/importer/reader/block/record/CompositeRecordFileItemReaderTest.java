// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.common.util.DomainUtils.createSha384Digest;
import static org.hiero.mirror.importer.reader.block.record.WrappedRecordBlockTestUtils.EXPECTED_RECORD_FILES;
import static org.hiero.mirror.importer.reader.block.record.WrappedRecordBlockTestUtils.readWrappedRecordBlocks;

import com.google.protobuf.ByteString;
import com.hedera.hapi.block.stream.protoc.RecordFileItem;
import com.hedera.services.stream.proto.ContractAction;
import com.hedera.services.stream.proto.ContractActions;
import com.hedera.services.stream.proto.ContractBytecode;
import com.hedera.services.stream.proto.ContractStateChange;
import com.hedera.services.stream.proto.ContractStateChanges;
import com.hedera.services.stream.proto.HashAlgorithm;
import com.hedera.services.stream.proto.HashObject;
import com.hedera.services.stream.proto.RecordStreamFile;
import com.hedera.services.stream.proto.RecordStreamItem;
import com.hedera.services.stream.proto.SidecarMetadata;
import com.hedera.services.stream.proto.SidecarType;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ThrowingConsumer;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.bouncycastle.util.encoders.Hex;
import org.hiero.mirror.common.domain.DigestAlgorithm;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.SidecarFile;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.exception.HashMismatchException;
import org.hiero.mirror.importer.parser.record.sidecar.SidecarProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
final class CompositeRecordFileItemReaderTest {

    private static final Transaction DEFAULT_TRANSACTION = Transaction.newBuilder()
            .setSignedTransactionBytes(SignedTransaction.newBuilder()
                    .setBodyBytes(TransactionBody.newBuilder()
                            .setCryptoTransfer(CryptoTransferTransactionBody.getDefaultInstance())
                            .build()
                            .toByteString())
                    .build()
                    .toByteString())
            .build();

    private static final RecursiveComparisonConfiguration RECORD_FILE_COMPARISON_CONFIG =
            RecursiveComparisonConfiguration.builder()
                    .withIgnoredFields("bytes", "items")
                    .build();

    private final SidecarProperties sidecarProperties = new SidecarProperties();
    private final RecordFileItemReader reader = new CompositeRecordFileItemReader(sidecarProperties);

    @BeforeEach
    void setup() {
        sidecarProperties.setEnabled(true);
    }

    @ParameterizedTest(name = "version - {1}")
    @MethodSource("readTestArgumentsProvider")
    void read(final RecordFileItem recordFileItem, final int version) {
        final var recordFile = reader.read(recordFileItem, version);
        assertThat(recordFile)
                .usingRecursiveComparison(RECORD_FILE_COMPARISON_CONFIG)
                .isEqualTo(EXPECTED_RECORD_FILES.get(
                        recordFileItem.getRecordFileContents().getBlockNumber()));
    }

    @Test
    void readUnsupportedVersion() {
        assertThatThrownBy(() -> reader.read(RecordFileItem.getDefaultInstance(), 0))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Unsupported record file version 0");
    }

    @Test
    void readWithSidecar() {
        // given
        final var recordFileItem = createRecordFileItemWithSidecar();

        // when
        final var recordFile = reader.read(recordFileItem, 6);

        // then
        assertRecordFileWithSidecars(recordFile, items -> assertThat(items)
                .extracting(recordItem -> recordItem.getSidecarRecords().size())
                .containsExactly(3, 0));
    }

    @ParameterizedTest
    @EnumSource(
            names = {"CONTRACT_ACTION", "CONTRACT_BYTECODE", "CONTRACT_STATE_CHANGE"},
            value = SidecarType.class)
    void readWithSidecarAndFilteredByType(final SidecarType type) {
        // given
        sidecarProperties.setTypes(Set.of(type));
        final var recordFileItem = createRecordFileItemWithSidecar();

        // when
        final var recordFile = reader.read(recordFileItem, 6);

        // then
        assertRecordFileWithSidecars(recordFile, items -> assertThat(items)
                .extracting(recordItem -> recordItem.getSidecarRecords().size())
                .containsExactly(1, 0));
    }

    @Test
    void readWithSidecarAndPersistBytes() {
        // given
        sidecarProperties.setPersistBytes(true);
        final var recordFileItem = createRecordFileItemWithSidecar();

        // when
        final var recordFile = reader.read(recordFileItem, 6);

        // then
        assertRecordFileWithSidecars(
                recordFile,
                items -> assertThat(items)
                        .extracting(recordItem -> recordItem.getSidecarRecords().size())
                        .containsExactly(3, 0),
                sidecarFiles -> assertThat(sidecarFiles).allSatisfy(sidecarFile -> assertThat(sidecarFile.getBytes())
                        .isNotEmpty()));
    }

    @Test
    void readWithSidecarDisabled() {
        // given
        sidecarProperties.setEnabled(false);
        final var recordFileItem = createRecordFileItemWithSidecar();

        // when
        final var recordFile = reader.read(recordFileItem, 6);

        // then
        assertRecordFileWithSidecars(recordFile, items -> assertThat(items)
                .extracting(recordItem -> recordItem.getSidecarRecords().size())
                .containsOnly(0));
    }

    @Test
    void readWithSidecarAndHashMismatch() {
        // given
        final var recordFileItem = createRecordFileItemWithSidecar();
        final var corrupted = recordFileItem.toBuilder()
                .setRecordFileContents(recordFileItem.getRecordFileContents().toBuilder()
                        .setSidecars(
                                0,
                                SidecarMetadata.newBuilder()
                                        .setHash(HashObject.newBuilder()
                                                .setAlgorithm(HashAlgorithm.SHA_384)
                                                .setHash(ByteString.copyFrom(TestUtils.generateRandomByteArray(48))))
                                        .setId(1)
                                        .build()))
                .build();

        // when, then
        assertThatThrownBy(() -> reader.read(corrupted, 6))
                .isInstanceOf(HashMismatchException.class)
                .hasMessageStartingWith("Sidecar hash mismatch for file");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3})
    void readWithSidecarAndMetadataIdOutOfBound(final int metadataIndex, final CapturedOutput output) {
        // given
        final var original = createRecordFileItemWithSidecar();
        final var recordFileItem = original.toBuilder()
                .setRecordFileContents(original.getRecordFileContents().toBuilder()
                        .addSidecars(SidecarMetadata.newBuilder()
                                .setId(metadataIndex)
                                .build())
                        .build())
                .build();

        // when
        final var recordFile = reader.read(recordFileItem, 6);

        // then
        assertRecordFileWithSidecars(recordFile, items -> assertThat(items)
                .extracting(recordItem -> recordItem.getSidecarRecords().size())
                .containsExactly(3, 0));
        assertThat(output.getAll())
                .contains(
                        "Recoverable error. Missing sidecar file content for id %d of wrapped record file 2025-08-02T02_55_14.210243Z.rcd"
                                .formatted(metadataIndex));
    }

    @Test
    void readWithAmendmentAddition() {
        // given - amendments insert items before, in the middle of, and after the existing items
        final var timestamp1 = TestUtils.toTimestamp(1_000_000_000L);
        final var timestamp2 = TestUtils.toTimestamp(2_000_000_000L);
        final var timestamp3 = TestUtils.toTimestamp(3_000_000_000L);
        final var timestamp4 = TestUtils.toTimestamp(4_000_000_000L);
        final var timestamp5 = TestUtils.toTimestamp(5_000_000_000L);
        final var recordFileItem = createRecordFileItemWithAmendments(
                List.of(recordStreamItem(timestamp2), recordStreamItem(timestamp4)),
                List.of(recordStreamItem(timestamp1), recordStreamItem(timestamp3), recordStreamItem(timestamp5)));

        // when
        final var recordFile = reader.read(recordFileItem, 6);

        // then
        final var expectedTimestamps = Stream.of(timestamp1, timestamp2, timestamp3, timestamp4, timestamp5)
                .map(DomainUtils::timestampInNanosMax)
                .toList();
        assertThat(recordFile)
                .returns(5L, RecordFile::getCount)
                .extracting(RecordFile::getItems)
                .asInstanceOf(InstanceOfAssertFactories.list(RecordItem.class))
                .extracting(RecordItem::getConsensusTimestamp)
                .containsExactlyElementsOf(expectedTimestamps);
    }

    @Test
    void readWithAmendmentReplacement() {
        // given - amendment replaces an existing item with a corrected transaction record
        final var timestamp1 = TestUtils.toTimestamp(1_000_000_000L);
        final var timestamp2 = TestUtils.toTimestamp(2_000_000_000L);
        final var amendedHash = ByteString.copyFrom(TestUtils.generateRandomByteArray(48));
        final var recordFileItem = createRecordFileItemWithAmendments(
                List.of(recordStreamItem(timestamp1), recordStreamItem(timestamp2)),
                List.of(recordStreamItemWithHash(timestamp2, amendedHash)));

        // when
        final var recordFile = reader.read(recordFileItem, 6);

        // then
        assertThat(recordFile)
                .returns(2L, RecordFile::getCount)
                .extracting(RecordFile::getItems)
                .asInstanceOf(InstanceOfAssertFactories.list(RecordItem.class))
                .hasSize(2)
                .last()
                .extracting(item -> item.getTransactionRecord().getTransactionHash())
                .isEqualTo(amendedHash);
    }

    @Test
    void readWithMixedAmendments() {
        // given - amendments include both additions (t2, t4) and a replacement (t3)
        final var timestamp1 = TestUtils.toTimestamp(1_000_000_000L);
        final var timestamp2 = TestUtils.toTimestamp(2_000_000_000L);
        final var timestamp3 = TestUtils.toTimestamp(3_000_000_000L);
        final var timestamp4 = TestUtils.toTimestamp(4_000_000_000L);
        final var amendedHash = ByteString.copyFrom(TestUtils.generateRandomByteArray(48));
        final var recordFileItem = createRecordFileItemWithAmendments(
                List.of(recordStreamItem(timestamp1), recordStreamItem(timestamp3)),
                List.of(
                        recordStreamItem(timestamp2),
                        recordStreamItemWithHash(timestamp3, amendedHash),
                        recordStreamItem(timestamp4)));

        // when
        final var recordFile = reader.read(recordFileItem, 6);

        // then
        final var expectedTimestamps = Stream.of(timestamp1, timestamp2, timestamp3, timestamp4)
                .map(DomainUtils::timestampInNanosMax)
                .toList();
        assertThat(recordFile)
                .returns(4L, RecordFile::getCount)
                .extracting(RecordFile::getItems)
                .asInstanceOf(InstanceOfAssertFactories.list(RecordItem.class))
                .satisfies(
                        items -> assertThat(items)
                                .extracting(RecordItem::getConsensusTimestamp)
                                .containsExactlyElementsOf(expectedTimestamps),
                        items -> assertThat(items.get(2).getTransactionRecord().getTransactionHash())
                                .isEqualTo(amendedHash));
    }

    private static void assertRecordFileWithSidecars(
            final RecordFile recordFile, final ThrowingConsumer<List<? extends RecordItem>> recordItemAssertion) {
        assertRecordFileWithSidecars(recordFile, recordItemAssertion, sidecars -> assertThat(sidecars)
                .allSatisfy(sidecar -> assertThat(sidecar.getBytes()).isNull()));
    }

    private static void assertRecordFileWithSidecars(
            final RecordFile recordFile,
            final ThrowingConsumer<List<? extends RecordItem>> recordItemAssertion,
            final ThrowingConsumer<List<? extends SidecarFile>> sidecarFileAssertion) {
        final var builder =
                SidecarFile.builder().consensusEnd(1754103314210243287L).hashAlgorithm(DigestAlgorithm.SHA_384);
        final var expectedSidecarFiles = List.of(
                builder.count(2)
                        .hash(
                                Hex.decode(
                                        "4357effa0b4b701f17570809bb89c1cfdf38130ae012931e09df8db83e98ac7cc7fe1f47317bb455fea4dd782213491c"))
                        .index(1)
                        .name("2025-08-02T02_55_14.210243Z_01.rcd")
                        .size(38)
                        .types(List.of(1, 2))
                        .build(),
                builder.count(1)
                        .hash(
                                Hex.decode(
                                        "2c4aad780af71b7f3a59b4cafbe65ecd8f1f5d6f67524840536b89c080014d1876f243b3038830da1cba096574bcf526"))
                        .index(2)
                        .name("2025-08-02T02_55_14.210243Z_02.rcd")
                        .size(17)
                        .types(List.of(3))
                        .build());
        assertThat(recordFile)
                .returns(2L, RecordFile::getCount)
                .returns("2025-08-02T02_55_14.210243Z.rcd", RecordFile::getName)
                .returns(2, RecordFile::getSidecarCount)
                .satisfies(
                        r -> assertThat(r)
                                .extracting(RecordFile::getItems)
                                .asInstanceOf(InstanceOfAssertFactories.list(RecordItem.class))
                                .hasSize(2)
                                .satisfies(recordItemAssertion),
                        r -> assertThat(r)
                                .extracting(
                                        RecordFile::getSidecars,
                                        InstanceOfAssertFactories.list(
                                                org.hiero.mirror.common.domain.transaction.SidecarFile.class))
                                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("bytes")
                                .containsExactlyInAnyOrderElementsOf(expectedSidecarFiles)
                                .satisfies(sidecarFileAssertion));
    }

    private static RecordFileItem createRecordFileItemWithSidecar() {
        final var consensusStart = TestUtils.toTimestamp(1754103314210243000L);
        final var consensusEnd = TestUtils.toTimestamp(1754103314210243287L);

        final var sidecarDigest = createSha384Digest();
        final var hashObjectBuilder = HashObject.newBuilder().setAlgorithm(HashAlgorithm.SHA_384);
        final var sidecarRecordBuilder = TransactionSidecarRecord.newBuilder().setConsensusTimestamp(consensusStart);
        final var sidecarFiles = List.of(
                com.hedera.services.stream.proto.SidecarFile.newBuilder()
                        .addSidecarRecords(sidecarRecordBuilder
                                .setActions(ContractActions.newBuilder()
                                        .addContractActions(ContractAction.getDefaultInstance()))
                                .build())
                        .addSidecarRecords(sidecarRecordBuilder
                                .setStateChanges(ContractStateChanges.newBuilder()
                                        .addContractStateChanges(ContractStateChange.getDefaultInstance()))
                                .build())
                        .build(),
                com.hedera.services.stream.proto.SidecarFile.newBuilder()
                        .addSidecarRecords(sidecarRecordBuilder
                                .setBytecode(ContractBytecode.getDefaultInstance())
                                .build())
                        .build());
        final var recordStreamFile = RecordStreamFile.newBuilder()
                .setHapiProtoVersion(SemanticVersion.newBuilder().setMinor(64))
                .setStartObjectRunningHash(hashObjectBuilder
                        .setHash(DomainUtils.fromBytes(TestUtils.generateRandomByteArray(48)))
                        .build())
                .addAllRecordStreamItems(List.of(
                        RecordStreamItem.newBuilder()
                                .setTransaction(DEFAULT_TRANSACTION)
                                .setRecord(TransactionRecord.newBuilder().setConsensusTimestamp(consensusStart))
                                .build(),
                        RecordStreamItem.newBuilder()
                                .setTransaction(DEFAULT_TRANSACTION)
                                .setRecord(TransactionRecord.newBuilder().setConsensusTimestamp(consensusEnd))
                                .build()))
                .setEndObjectRunningHash(hashObjectBuilder
                        .setHash(DomainUtils.fromBytes(TestUtils.generateRandomByteArray(48)))
                        .build())
                .setBlockNumber(82697486L)
                .addAllSidecars(List.of(
                        SidecarMetadata.newBuilder()
                                .setHash(hashObjectBuilder
                                        .setHash(DomainUtils.fromBytes(sidecarDigest.digest(
                                                sidecarFiles.getFirst().toByteArray())))
                                        .build())
                                .setId(1)
                                .addAllTypes(List.of(SidecarType.CONTRACT_STATE_CHANGE, SidecarType.CONTRACT_ACTION))
                                .build(),
                        SidecarMetadata.newBuilder()
                                .setHash(hashObjectBuilder
                                        .setHash(DomainUtils.fromBytes(sidecarDigest.digest(
                                                sidecarFiles.getLast().toByteArray())))
                                        .build())
                                .setId(2)
                                .addTypes(SidecarType.CONTRACT_BYTECODE)
                                .build()))
                .build();
        return RecordFileItem.newBuilder()
                .setCreationTime(consensusStart)
                .setRecordFileContents(recordStreamFile)
                .addAllSidecarFileContents(sidecarFiles)
                .build();
    }

    private static RecordStreamItem recordStreamItem(final Timestamp consensusTimestamp) {
        return recordStreamItemWithHash(consensusTimestamp, ByteString.EMPTY);
    }

    private static RecordStreamItem recordStreamItemWithHash(
            final Timestamp consensusTimestamp, final ByteString transactionHash) {
        return RecordStreamItem.newBuilder()
                .setTransaction(DEFAULT_TRANSACTION)
                .setRecord(TransactionRecord.newBuilder()
                        .setConsensusTimestamp(consensusTimestamp)
                        .setTransactionHash(transactionHash))
                .build();
    }

    private static RecordFileItem createRecordFileItemWithAmendments(
            final List<RecordStreamItem> items, final List<RecordStreamItem> amendments) {
        final var hashObjectBuilder = HashObject.newBuilder().setAlgorithm(HashAlgorithm.SHA_384);
        final var recordStreamFile = RecordStreamFile.newBuilder()
                .setHapiProtoVersion(SemanticVersion.newBuilder().setMinor(64))
                .setStartObjectRunningHash(hashObjectBuilder
                        .setHash(DomainUtils.fromBytes(TestUtils.generateRandomByteArray(48)))
                        .build())
                .addAllRecordStreamItems(items)
                .setEndObjectRunningHash(hashObjectBuilder
                        .setHash(DomainUtils.fromBytes(TestUtils.generateRandomByteArray(48)))
                        .build())
                .setBlockNumber(1L)
                .build();
        return RecordFileItem.newBuilder()
                .setCreationTime(items.getFirst().getRecord().getConsensusTimestamp())
                .setRecordFileContents(recordStreamFile)
                .addAllAmendments(amendments)
                .build();
    }

    private static Stream<Arguments> readTestArgumentsProvider() {
        return readWrappedRecordBlocks().stream().map(block -> {
            final var blockProof = block.getItems(3).getBlockProof();
            final int version = blockProof.getSignedRecordFileProof().getVersion();
            return Arguments.of(block.getItems(1).getRecordFile(), version);
        });
    }
}
