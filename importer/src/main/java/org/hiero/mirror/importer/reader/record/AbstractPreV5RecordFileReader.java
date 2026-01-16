// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.record;

import static org.hiero.mirror.common.util.DomainUtils.createSha384Digest;

import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.binary.Hex;
import org.hiero.mirror.common.domain.DigestAlgorithm;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.exception.ImporterException;
import org.hiero.mirror.importer.exception.StreamFileReaderException;
import org.hiero.mirror.importer.reader.ValidatedDataInputStream;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
@RequiredArgsConstructor
public abstract class AbstractPreV5RecordFileReader implements RecordFileReader {

    protected static final DigestAlgorithm DIGEST_ALGORITHM = DigestAlgorithm.SHA_384;
    protected static final byte PREV_HASH_MARKER = 1;
    protected static final byte RECORD_MARKER = 2;

    private final int readerVersion;

    @Override
    public RecordFile read(StreamFileData streamFileData) {
        String filename = streamFileData.getFilename();

        try (RecordFileDigest digest = getRecordFileDigest(streamFileData.getInputStream());
                ValidatedDataInputStream vdis = new ValidatedDataInputStream(digest.getDigestInputStream(), filename)) {
            byte[] bytes = streamFileData.getBytes();
            RecordFile recordFile = new RecordFile();
            recordFile.setBytes(bytes);
            recordFile.setDigestAlgorithm(DIGEST_ALGORITHM);
            recordFile.setLoadStart(streamFileData.getStreamFilename().getTimestamp());
            recordFile.setName(filename);
            recordFile.setSize(bytes.length);

            readHeader(vdis, recordFile);
            readBody(vdis, digest, recordFile);

            return recordFile;
        } catch (ImporterException e) {
            throw e;
        } catch (Exception e) {
            throw new StreamFileReaderException("Error reading record file " + filename, e);
        }
    }

    protected abstract RecordFileDigest getRecordFileDigest(InputStream is);

    /**
     * Reads the record file header, updates the message digest with data from the header, and sets corresponding
     * {@link RecordFile} fields. {@code vdis} should point at the beginning of the stream. The header should contain
     * file version, HAPI version, and the previous file hash.
     *
     * @param vdis       the {@link ValidatedDataInputStream} of the record file
     * @param recordFile the {@link RecordFile} object
     * @throws IOException
     */
    private void readHeader(ValidatedDataInputStream vdis, RecordFile recordFile) throws IOException {
        int version = vdis.readInt(readerVersion, "record file version");
        vdis.readInt(); // HAPI version, not used
        vdis.readByte(PREV_HASH_MARKER, "previous hash marker");
        byte[] prevHash = vdis.readNBytes(DIGEST_ALGORITHM.getSize(), "previous hash");

        recordFile.setVersion(version);
        recordFile.setPreviousHash(Hex.encodeHexString(prevHash));
    }

    /**
     * Reads the record file body, updates the message digest with data from the body, and sets corresponding
     * {@link RecordFile} fields. {@code vdis} should point at the beginning of the body. The body should contain a
     * variable number of transaction and record pairs ordered by consensus timestamp. The body may also contain
     * metadata to mark the boundary of the pairs.
     *
     * @param vdis       the {@link ValidatedDataInputStream} of the record file
     * @param digest     the {@link RecordFileDigest} to update the digest with
     * @param recordFile the {@link RecordFile} object
     * @throws IOException
     */
    private void readBody(ValidatedDataInputStream vdis, RecordFileDigest digest, RecordFile recordFile)
            throws IOException {
        int count = 0;
        long consensusStart = 0;
        long consensusEnd = 0;
        digest.startBody();
        List<RecordItem> items = new ArrayList<>();
        RecordItem lastRecordItem = null;

        while (vdis.available() != 0) {
            vdis.readByte(RECORD_MARKER, "record marker");
            byte[] transactionBytes = vdis.readLengthAndBytes(1, MAX_TRANSACTION_LENGTH, false, "transaction bytes");
            byte[] recordBytes = vdis.readLengthAndBytes(1, MAX_TRANSACTION_LENGTH, false, "record bytes");
            RecordItem recordItem = RecordItem.builder()
                    .hapiVersion(recordFile.getHapiVersion())
                    .previous(lastRecordItem)
                    .transactionRecord(TransactionRecord.parseFrom(recordBytes))
                    .transactionIndex(count)
                    .transaction(Transaction.parseFrom(transactionBytes))
                    .build();
            items.add(recordItem);

            if (count == 0) {
                consensusStart = recordItem.getConsensusTimestamp();
            }

            if (vdis.available() == 0) {
                consensusEnd = recordItem.getConsensusTimestamp();
            }

            lastRecordItem = recordItem;
            count++;
        }

        String fileHash = Hex.encodeHexString(digest.digest());

        recordFile.setConsensusStart(consensusStart);
        recordFile.setConsensusEnd(consensusEnd);
        recordFile.setCount((long) count);
        recordFile.setFileHash(fileHash);
        recordFile.setHash(fileHash);
        recordFile.setItems(items);
    }

    protected static class RecordFileDigest implements AutoCloseable {

        @Getter
        private final DigestInputStream digestInputStream;

        private final MessageDigest messageDigestFile;

        private final @Nullable MessageDigest messageDigestBody;

        public RecordFileDigest(InputStream is, boolean simple) {
            messageDigestFile = createSha384Digest();
            digestInputStream = new DigestInputStream(is, messageDigestFile);

            if (simple) {
                messageDigestBody = null;
            } else {
                // calculate the hash of the body separately, and the file hash is calculated as
                // h(header | h(body))
                messageDigestBody = createSha384Digest();
            }
        }

        public byte[] digest() {
            if (messageDigestBody != null) {
                messageDigestFile.update(messageDigestBody.digest());
            }

            return messageDigestFile.digest();
        }

        public void startBody() {
            if (messageDigestBody != null) {
                digestInputStream.setMessageDigest(messageDigestBody);
            }
        }

        @Override
        public void close() throws IOException {
            digestInputStream.close();
        }
    }
}
