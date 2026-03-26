// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.record;

import static org.hiero.mirror.common.domain.StreamType.RECORD;
import static org.hiero.mirror.common.util.DomainUtils.createSha384Digest;
import static org.hiero.mirror.common.util.DomainUtils.getHashBytes;
import static org.hiero.mirror.importer.domain.StreamFilename.FileType.DATA;

import com.hedera.hapi.block.stream.protoc.RecordFileItem;
import com.hedera.services.stream.proto.RecordStreamItem;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.codec.binary.Hex;
import org.hiero.mirror.common.domain.DigestAlgorithm;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.importer.domain.StreamFilename;
import org.hiero.mirror.importer.exception.BlockStreamException;

abstract class AbstractRecordFileItemParser implements RecordFileItemParser {

    @Override
    public RecordFile parse(final long blockNumber, final RecordFileItem recordFileItem, final int version) {
        try (final var bos = new ByteArrayOutputStream();
                final var hos = new DigestOutputStream(bos, createSha384Digest());
                final var dos = new DataOutputStream(hos)) {
            final var context =
                    new Context(dos, hos.getMessageDigest(), hos, new ArrayList<>(), new RecordFile(), recordFileItem);

            dos.writeInt(version);
            onHeader(context);
            onBody(context);
            onEnd(context);

            // Set other common fields. Note metadata hash calculation is skipped because there is only signature for
            // file hash in SignedRecordFileProof
            final var items = context.items();
            final var recordFile = context.recordFile();
            final byte[] bytes = bos.toByteArray();
            recordFile.setBytes(bytes);
            recordFile.setConsensusEnd(items.getLast().getConsensusTimestamp());
            recordFile.setConsensusStart(items.getFirst().getConsensusTimestamp());
            recordFile.setCount((long) items.size());
            recordFile.setDigestAlgorithm(DigestAlgorithm.SHA_384);
            recordFile.setFileHash(Hex.encodeHexString(context.fileDigest().digest()));
            recordFile.setIndex(blockNumber);
            recordFile.setItems(items);
            recordFile.setSize(bytes.length);
            recordFile.setVersion(version);
            finalize(recordFile);

            return recordFile;
        } catch (final IOException ex) {
            throw new BlockStreamException("Failed to parse RecordFileItem", ex);
        }
    }

    protected void finalize(final RecordFile recordFile) {}

    protected void onBody(final Context context) throws IOException {
        final var recordStreamFile = context.recordFileItem().getRecordFileContents();
        for (final var recordStreamItem : recordStreamFile.getRecordStreamItemsList()) {
            onRecordStreamItem(context, recordStreamItem);
        }
    }

    protected void onEnd(final Context context) throws IOException {
        final var recordStreamFile = context.recordFileItem().getRecordFileContents();
        if (recordStreamFile.hasEndObjectRunningHash()) {
            context.recordFile().setHash(Hex.encodeHexString(getHashBytes(recordStreamFile.getEndObjectRunningHash())));
        }
    }

    protected void onHeader(final Context context) throws IOException {
        final var recordStreamFile = context.recordFileItem().getRecordFileContents();
        final var hapiProtoVersion = recordStreamFile.getHapiProtoVersion();
        final var recordFile = context.recordFile();
        recordFile.setHapiVersionMajor(hapiProtoVersion.getMajor());
        recordFile.setHapiVersionMinor(hapiProtoVersion.getMinor());
        recordFile.setHapiVersionPatch(hapiProtoVersion.getPatch());
        recordFile.setSoftwareVersionMajor(hapiProtoVersion.getMajor());
        recordFile.setSoftwareVersionMinor(hapiProtoVersion.getMinor());
        recordFile.setSoftwareVersionPatch(hapiProtoVersion.getPatch());

        final var creationTime = context.recordFileItem().getCreationTime();
        final var fileInstant = Instant.ofEpochSecond(creationTime.getSeconds(), creationTime.getNanos());
        final var filename = StreamFilename.getFilename(RECORD, DATA, fileInstant);
        recordFile.setName(filename);

        recordFile.setPreviousHash(Hex.encodeHexString(getHashBytes(recordStreamFile.getStartObjectRunningHash())));
    }

    protected void onRecordStreamItem(final Context context, final RecordStreamItem recordStreamItem)
            throws IOException {
        final var items = context.items();
        final var recordItem = RecordItem.builder()
                .hapiVersion(context.recordFile().getHapiVersion())
                .previous(items.isEmpty() ? null : items.getLast())
                .transactionRecord(recordStreamItem.getRecord())
                .transaction(recordStreamItem.getTransaction())
                .transactionIndex(items.size())
                .build();
        items.add(recordItem);
    }

    protected record Context(
            DataOutputStream dos,
            MessageDigest fileDigest,
            DigestOutputStream hos,
            List<RecordItem> items,
            RecordFile recordFile,
            RecordFileItem recordFileItem) {}
}
