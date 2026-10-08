// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.record;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.importer.reader.record.AbstractPreV5RecordFileReader.MAX_RECORD_ITEMS;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.junit.jupiter.api.Test;

class RecordFileReaderImplV1Test extends AbstractRecordFileReaderTest {

    @Override
    protected RecordFileReader getRecordFileReader() {
        return new RecordFileReaderImplV1();
    }

    @Override
    protected boolean filterFile(int version) {
        return version == 1;
    }

    @Test
    void readFileExceedingMaxRecordCount() throws Exception {
        byte[] transactionBytes = Transaction.newBuilder()
                .setBodyBytes(ByteString.copyFrom(
                        TransactionBody.newBuilder().setMemo("x").build().toByteArray()))
                .build()
                .toByteArray();
        byte[] recordBytes = TransactionRecord.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(1L))
                .build()
                .toByteArray();

        var contents = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(contents)) {
            output.writeInt(1);
            output.writeInt(0);
            output.writeByte(AbstractPreV5RecordFileReader.PREV_HASH_MARKER);
            output.write(new byte[AbstractPreV5RecordFileReader.DIGEST_ALGORITHM.getSize()]);

            for (int i = 0; i <= MAX_RECORD_ITEMS; i++) {
                output.writeByte(AbstractPreV5RecordFileReader.RECORD_MARKER);
                output.writeInt(transactionBytes.length);
                output.write(transactionBytes);
                output.writeInt(recordBytes.length);
                output.write(recordBytes);
            }
        }

        var streamFileData = StreamFileData.from("2019-07-01T14_13_00.317763Z.rcd", contents.toByteArray());

        assertThatThrownBy(() -> recordFileReader.read(streamFileData))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessage("Record file 2019-07-01T14_13_00.317763Z.rcd contains more than 10000 records");
    }
}
