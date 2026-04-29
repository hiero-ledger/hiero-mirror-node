// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.hiero.mirror.importer.TestUtils.gzip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.hedera.services.stream.proto.HashAlgorithm;
import com.hedera.services.stream.proto.HashObject;
import com.hedera.services.stream.proto.RecordStreamFile;
import com.hedera.services.stream.proto.RecordStreamItem;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.time.Instant;
import java.util.function.Function;
import org.apache.commons.lang3.Strings;
import org.hiero.mirror.common.domain.DigestAlgorithm;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.junit.jupiter.api.Test;

final class ProtoRecordFileReaderTest extends AbstractRecordFileReaderTest {

    private static final String FILENAME = "2022-06-21T09_15_38.325469003Z.rcd.gz";
    private static final long FILE_TIMESTAMP = DomainUtils.convertToNanosMax(
            Instant.parse(Strings.CS.removeEnd(FILENAME, ".rcd.gz").replace("_", ":")));

    @Override
    protected RecordFileReader getRecordFileReader() {
        return new ProtoRecordFileReader();
    }

    @Override
    protected boolean filterFile(int version) {
        return version == 6;
    }

    @Test
    void testEmptyRecordStreamItems() {
        var bytes = gzip(ProtoRecordStreamFile.of(RecordStreamFile.Builder::clearRecordStreamItems));
        var reader = new ProtoRecordFileReader();
        var streamFileData = StreamFileData.from(FILENAME, bytes);
        final var recordFile = reader.read(streamFileData);
        assertThat(recordFile)
                .returns(FILE_TIMESTAMP, RecordFile::getConsensusEnd)
                .returns(FILE_TIMESTAMP, RecordFile::getConsensusStart)
                .returns(0L, RecordFile::getCount)
                .returns(FILENAME, RecordFile::getName)
                .extracting(RecordFile::getItems, LIST)
                .isEmpty();
    }

    @Test
    void testInvalidHashAlgorithm() {
        var bytes = gzip(ProtoRecordStreamFile.of(b -> {
            b.getStartObjectRunningHashBuilder().setAlgorithm(HashAlgorithm.HASH_ALGORITHM_UNKNOWN);
            b.getEndObjectRunningHashBuilder().setAlgorithm(HashAlgorithm.HASH_ALGORITHM_UNKNOWN);
            return b;
        }));
        var reader = new ProtoRecordFileReader();
        var streamFileData = StreamFileData.from(FILENAME, bytes);
        var exception = assertThrows(InvalidStreamFileException.class, () -> reader.read(streamFileData));
        var expected = String.format(
                "%s has unsupported start running object hash algorithm "
                        + "HASH_ALGORITHM_UNKNOWN and end running object hash algorithm HASH_ALGORITHM_UNKNOWN",
                FILENAME);
        assertEquals(expected, exception.getMessage());
    }

    @Test
    void testMismatchRunningObjectHashAlgorithms() {
        var bytes = gzip(ProtoRecordStreamFile.of(b -> {
            b.getStartObjectRunningHashBuilder().setAlgorithm(HashAlgorithm.HASH_ALGORITHM_UNKNOWN);
            return b;
        }));
        var reader = new ProtoRecordFileReader();
        var streamFileData = StreamFileData.from(FILENAME, bytes);
        var recordFile = reader.read(streamFileData);

        assertThat(recordFile.getDigestAlgorithm()).isEqualTo(DigestAlgorithm.SHA_384);
    }

    private static class ProtoRecordStreamFile {

        private static final int VERSION = 6;

        private static byte[] of(Function<RecordStreamFile.Builder, RecordStreamFile.Builder> customizer) {
            return Bytes.concat(
                    Ints.toByteArray(VERSION),
                    customizer
                            .apply(getDefaultRecordStreamFileBuilder())
                            .build()
                            .toByteArray());
        }

        private static RecordStreamFile.Builder getDefaultRecordStreamFileBuilder() {
            var hashObject =
                    HashObject.newBuilder().setAlgorithm(HashAlgorithm.SHA_384).setLength(48);
            var recordStreamItem = RecordStreamItem.newBuilder()
                    .setTransaction(Transaction.newBuilder()
                            .setSignedTransactionBytes(SignedTransaction.newBuilder()
                                    .setBodyBytes(TransactionBody.newBuilder()
                                            .setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()
                                                    .build())
                                            .build()
                                            .toByteString())
                                    .build()
                                    .toByteString()))
                    .setRecord(TransactionRecord.newBuilder())
                    .build();
            return RecordStreamFile.newBuilder()
                    .setHapiProtoVersion(SemanticVersion.newBuilder().setMajor(27))
                    .setStartObjectRunningHash(hashObject
                            .setHash(DomainUtils.fromBytes(TestUtils.generateRandomByteArray(48)))
                            .build())
                    .addRecordStreamItems(recordStreamItem)
                    .setEndObjectRunningHash(hashObject
                            .setHash(DomainUtils.fromBytes(TestUtils.generateRandomByteArray(48)))
                            .build())
                    .setBlockNumber(100L);
        }
    }
}
