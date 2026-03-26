// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.record;

import com.hedera.services.stream.proto.HashObject;
import com.hedera.services.stream.proto.RecordStreamItem;
import java.io.DataOutputStream;
import java.io.IOException;
import org.hiero.mirror.common.domain.DigestAlgorithm;
import org.hiero.mirror.common.util.DomainUtils;

final class RecordFileItemParserV5 extends AbstractRecordFileItemParser {

    private static final long HASH_OBJECT_CLASS_ID = 0xf422da83a251741eL;
    private static final int HASH_OBJECT_CLASS_VERSION = 1;
    private static final int OBJECT_STREAM_VERSION = 1;
    private static final long RECORD_STREAM_OBJECT_CLASS_ID = 0xe370929ba5429d8bL;
    private static final int RECORD_STREAM_OBJECT_CLASS_VERSION = 1;

    @Override
    protected void onEnd(final Context context) throws IOException {
        super.onEnd(context);
        writeHashObject(
                context.dos(), context.recordFileItem().getRecordFileContents().getEndObjectRunningHash());
    }

    @Override
    protected void onHeader(final Context context) throws IOException {
        super.onHeader(context);

        final var dos = context.dos();
        final var recordStreamFile = context.recordFileItem().getRecordFileContents();
        final var hapiProtoVersion = recordStreamFile.getHapiProtoVersion();
        dos.writeInt(hapiProtoVersion.getMajor());
        dos.writeInt(hapiProtoVersion.getMinor());
        dos.writeInt(hapiProtoVersion.getPatch());
        dos.writeInt(OBJECT_STREAM_VERSION);
        writeHashObject(dos, context.recordFileItem().getRecordFileContents().getStartObjectRunningHash());
    }

    @Override
    protected void onRecordStreamItem(final Context context, final RecordStreamItem recordStreamItem)
            throws IOException {
        super.onRecordStreamItem(context, recordStreamItem);

        final var dos = context.dos();
        dos.writeLong(RECORD_STREAM_OBJECT_CLASS_ID);
        dos.writeInt(RECORD_STREAM_OBJECT_CLASS_VERSION);

        final var recordBytes = recordStreamItem.getRecord().toByteArray();
        dos.writeInt(recordBytes.length);
        dos.write(recordBytes);

        final var transactionBytes = recordStreamItem.getTransaction().toByteArray();
        dos.writeInt(transactionBytes.length);
        dos.write(transactionBytes);
    }

    private static void writeHashObject(final DataOutputStream dos, final HashObject hashObject) throws IOException {
        dos.writeLong(HASH_OBJECT_CLASS_ID);
        dos.writeInt(HASH_OBJECT_CLASS_VERSION);
        dos.writeInt(DigestAlgorithm.SHA_384.getType());
        final var hashBytes = DomainUtils.getHashBytes(hashObject);
        dos.writeInt(hashBytes.length);
        dos.write(hashBytes);
    }
}
