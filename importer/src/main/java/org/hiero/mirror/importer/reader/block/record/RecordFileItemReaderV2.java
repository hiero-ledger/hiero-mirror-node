// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.record;

import static org.hiero.mirror.common.util.DomainUtils.createSha384Digest;

import com.hedera.services.stream.proto.RecordStreamItem;
import java.io.IOException;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.util.DomainUtils;

final class RecordFileItemReaderV2 extends AbstractRecordFileItemReader {

    private static final byte PREV_HASH_MARKER = 0x01;
    private static final byte RECORD_MARKER = 0x02;

    @Override
    protected void finalize(final RecordFile recordFile) {
        recordFile.setHash(recordFile.getFileHash());
    }

    @Override
    protected void onBody(final Context context) throws IOException {
        // V2 file hash is calculated as h(header | h(body)), so switch to a new SHA-384 digest for the body
        context.hos().setMessageDigest(createSha384Digest());
        super.onBody(context);
    }

    @Override
    protected void onEnd(final Context context) throws IOException {
        super.onEnd(context);
        // Append the body hash
        context.fileDigest().update(context.hos().getMessageDigest().digest());
    }

    @Override
    protected void onHeader(final Context context) throws IOException {
        super.onHeader(context);

        // reset HAPI version to be consistent with RecordFileReaderImplV2 - ignore the HAPI version in v2 record files
        final var recordFile = context.recordFile();
        recordFile.setHapiVersionMajor(null);
        recordFile.setHapiVersionMinor(null);
        recordFile.setHapiVersionPatch(null);
        recordFile.setSoftwareVersionMajor(null);
        recordFile.setSoftwareVersionMinor(null);
        recordFile.setSoftwareVersionPatch(null);

        final var dos = context.dos();
        final var recordStreamFile = context.recordFileItem().getRecordFileContents();
        dos.writeInt(recordStreamFile.getHapiProtoVersion().getMinor());
        dos.writeByte(PREV_HASH_MARKER);
        dos.write(DomainUtils.getHashBytes(recordStreamFile.getStartObjectRunningHash()));
    }

    @Override
    protected void onRecordStreamItem(final Context context, final RecordStreamItem recordStreamItem)
            throws IOException {
        super.onRecordStreamItem(context, recordStreamItem);

        final var dos = context.dos();
        dos.writeByte(RECORD_MARKER);
        final var transactionBytes = recordStreamItem.getTransaction().toByteArray();
        dos.writeInt(transactionBytes.length);
        dos.write(transactionBytes);

        final var recordBytes = recordStreamItem.getRecord().toByteArray();
        dos.writeInt(recordBytes.length);
        dos.write(recordBytes);
    }
}
