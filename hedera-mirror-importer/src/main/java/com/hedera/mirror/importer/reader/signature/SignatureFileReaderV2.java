// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.reader.signature;

import com.hedera.mirror.common.domain.DigestAlgorithm;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.domain.StreamFileSignature;
import com.hedera.mirror.importer.domain.StreamFileSignature.SignatureType;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import com.hedera.mirror.importer.exception.SignatureFileParsingException;
import com.hedera.mirror.importer.reader.ValidatedDataInputStream;
import jakarta.inject.Named;
import java.io.IOException;

@Named
public class SignatureFileReaderV2 implements SignatureFileReader {

    protected static final byte SIGNATURE_TYPE_SIGNATURE = 3; // the file content signature, should not be hashed
    protected static final byte SIGNATURE_TYPE_FILE_HASH = 4; // next 48 bytes are SHA-384 of content of record file

    private static final byte VERSION = 2;

    @Override
    public StreamFileSignature read(StreamFileData signatureFileData) {
        String filename = signatureFileData.getFilename();

        try (ValidatedDataInputStream vdis =
                new ValidatedDataInputStream(signatureFileData.getInputStream(), filename)) {
            vdis.readByte(SIGNATURE_TYPE_FILE_HASH, "hash delimiter");
            byte[] fileHash = vdis.readNBytes(DigestAlgorithm.SHA_384.getSize(), "hash");

            vdis.readByte(SIGNATURE_TYPE_SIGNATURE, "signature delimiter");
            byte[] signature =
                    vdis.readLengthAndBytes(1, SignatureType.SHA_384_WITH_RSA.getMaxLength(), false, "signature");

            if (vdis.available() != 0) {
                throw new SignatureFileParsingException("Extra data discovered in signature file " + filename);
            }

            StreamFileSignature streamFileSignature = new StreamFileSignature();
            streamFileSignature.setBytes(signatureFileData.getBytes());
            streamFileSignature.setFileHash(fileHash);
            streamFileSignature.setFileHashSignature(signature);
            streamFileSignature.setFilename(signatureFileData.getStreamFilename());
            streamFileSignature.setSignatureType(SignatureType.SHA_384_WITH_RSA);
            streamFileSignature.setVersion(VERSION);

            return streamFileSignature;
        } catch (InvalidStreamFileException | IOException e) {
            throw new SignatureFileParsingException(e);
        }
    }
}
