// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.record;

import static org.hiero.mirror.common.util.DomainUtils.createSha384Digest;

import com.google.common.primitives.Longs;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import jakarta.inject.Named;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.codec.binary.Hex;
import org.hiero.mirror.common.domain.DigestAlgorithm;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.hiero.mirror.importer.exception.StreamFileReaderException;
import org.hiero.mirror.importer.reader.AbstractStreamObject;
import org.hiero.mirror.importer.reader.HashObject;
import org.hiero.mirror.importer.reader.ValidatedDataInputStream;

@Named
public class RecordFileReaderImplV5 implements RecordFileReader {

    private static final DigestAlgorithm DIGEST_ALGORITHM = DigestAlgorithm.SHA_384;
    private static final int VERSION = 5;

    @Override
    public RecordFile read(StreamFileData streamFileData) {
        MessageDigest messageDigestFile = createSha384Digest();
        MessageDigest messageDigestMetadata = createSha384Digest();
        String filename = streamFileData.getFilename();

        // the first DigestInputStream is for file hash and the second is for metadata hash. Any BufferedInputStream
        // should not wrap, directly or indirectly, the second DigestInputStream. The BufferedInputStream after the
        // first DigestInputStream is needed to avoid digesting some class ID fields twice.
        try (DigestInputStream digestInputStream = new DigestInputStream(
                        new BufferedInputStream(
                                new DigestInputStream(streamFileData.getInputStream(), messageDigestFile)),
                        messageDigestMetadata);
                ValidatedDataInputStream vdis = new ValidatedDataInputStream(digestInputStream, filename)) {
            byte[] bytes = streamFileData.getBytes();
            RecordFile recordFile = new RecordFile();
            recordFile.setBytes(bytes);
            recordFile.setDigestAlgorithm(DIGEST_ALGORITHM);
            recordFile.setLoadStart(streamFileData.getStreamFilename().getTimestamp());
            recordFile.setName(filename);
            recordFile.setSize(bytes.length);

            readHeader(vdis, recordFile);
            readBody(vdis, digestInputStream, recordFile);

            recordFile.setFileHash(Hex.encodeHexString(messageDigestFile.digest()));
            recordFile.setMetadataHash(Hex.encodeHexString(messageDigestMetadata.digest()));

            return recordFile;
        } catch (IOException e) {
            throw new StreamFileReaderException("Error reading record file " + filename, e);
        }
    }

    private void readHeader(ValidatedDataInputStream vdis, RecordFile recordFile) throws IOException {
        vdis.readInt(VERSION, "record file version");
        recordFile.setHapiVersionMajor(vdis.readInt());
        recordFile.setHapiVersionMinor(vdis.readInt());
        recordFile.setHapiVersionPatch(vdis.readInt());
        recordFile.setSoftwareVersionMajor(recordFile.getHapiVersionMajor());
        recordFile.setSoftwareVersionMinor(recordFile.getHapiVersionMinor());
        recordFile.setSoftwareVersionPatch(recordFile.getHapiVersionPatch());
        recordFile.setVersion(VERSION);
    }

    private void readBody(
            ValidatedDataInputStream vdis, DigestInputStream metadataDigestInputStream, RecordFile recordFile)
            throws IOException {
        String filename = recordFile.getName();

        vdis.readInt(); // object stream version

        // start object running hash
        HashObject startHashObject = new HashObject(vdis, DIGEST_ALGORITHM);
        metadataDigestInputStream.on(false); // metadata hash is not calculated on record stream objects
        long hashObjectClassId = startHashObject.getClassId();

        int count = 0;
        long consensusStart = 0;
        List<RecordItem> items = new ArrayList<>();
        RecordItem lastRecordItem = null;

        // read record stream objects
        while (!isHashObject(vdis, hashObjectClassId)) {
            RecordStreamObject recordStreamObject = new RecordStreamObject(vdis);
            var recordItem = RecordItem.builder()
                    .hapiVersion(recordFile.getHapiVersion())
                    .previous(lastRecordItem)
                    .transactionRecord(TransactionRecord.parseFrom(recordStreamObject.recordBytes))
                    .transactionIndex(count)
                    .transaction(Transaction.parseFrom(recordStreamObject.transactionBytes))
                    .build();

            items.add(recordItem);

            if (count == 0) {
                consensusStart = recordItem.getConsensusTimestamp();
            }

            lastRecordItem = recordItem;
            count++;
        }

        if (count == 0) {
            throw new InvalidStreamFileException("No record stream objects in record file " + filename);
        }
        long consensusEnd = lastRecordItem.getConsensusTimestamp();

        // end object running hash, metadata hash is calculated on it
        metadataDigestInputStream.on(true);
        HashObject endHashObject = new HashObject(vdis, DIGEST_ALGORITHM);

        if (vdis.available() != 0) {
            throw new InvalidStreamFileException("Extra data discovered in record file " + filename);
        }

        recordFile.setCount((long) count);
        recordFile.setConsensusEnd(consensusEnd);
        recordFile.setConsensusStart(consensusStart);
        recordFile.setHash(Hex.encodeHexString(endHashObject.getHash()));
        recordFile.setItems(items);
        recordFile.setPreviousHash(Hex.encodeHexString(startHashObject.getHash()));
    }

    private boolean isHashObject(DataInputStream dis, long hashObjectClassId) throws IOException {
        dis.mark(Longs.BYTES);
        long classId = dis.readLong();
        dis.reset();

        return classId == hashObjectClassId;
    }

    @EqualsAndHashCode(callSuper = true)
    @Getter
    private static class RecordStreamObject extends AbstractStreamObject {

        private static final int MAX_RECORD_LENGTH = 64 * 1024;

        private final byte[] recordBytes;
        private final byte[] transactionBytes;

        RecordStreamObject(ValidatedDataInputStream vdis) {
            super(vdis);

            try {
                recordBytes = vdis.readLengthAndBytes(1, MAX_RECORD_LENGTH, false, "record bytes");
                transactionBytes = vdis.readLengthAndBytes(1, MAX_TRANSACTION_LENGTH, false, "transaction bytes");
            } catch (IOException e) {
                throw new InvalidStreamFileException(e);
            }
        }
    }
}
