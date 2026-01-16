// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.signature;

import static org.hiero.mirror.common.domain.DigestAlgorithm.SHA_384;

import jakarta.inject.Named;
import java.io.IOException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.domain.StreamFileSignature;
import org.hiero.mirror.importer.domain.StreamFileSignature.SignatureType;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.hiero.mirror.importer.exception.SignatureFileParsingException;
import org.hiero.mirror.importer.reader.AbstractStreamObject;
import org.hiero.mirror.importer.reader.HashObject;
import org.hiero.mirror.importer.reader.ValidatedDataInputStream;

@Named
public class SignatureFileReaderV5 implements SignatureFileReader {

    protected static final byte VERSION = 5;

    @Override
    public StreamFileSignature read(StreamFileData signatureFileData) {
        String filename = signatureFileData.getFilename();

        try (ValidatedDataInputStream vdis =
                new ValidatedDataInputStream(signatureFileData.getInputStream(), filename)) {
            vdis.readByte(VERSION, "fileVersion");

            // Read the objectStreamSignatureVersion, which is not used
            vdis.readInt();

            HashObject fileHashObject = new HashObject(vdis, "entireFile", SHA_384);
            SignatureObject fileHashSignatureObject = new SignatureObject(vdis, "entireFile");

            HashObject metadataHashObject = new HashObject(vdis, "metadata", SHA_384);
            SignatureObject metadataHashSignatureObject = new SignatureObject(vdis, "metadata");

            if (vdis.available() != 0) {
                throw new SignatureFileParsingException("Extra data discovered in signature file " + filename);
            }

            StreamFileSignature streamFileSignature = new StreamFileSignature();
            streamFileSignature.setBytes(signatureFileData.getBytes());
            streamFileSignature.setFileHash(fileHashObject.getHash());
            streamFileSignature.setFileHashSignature(fileHashSignatureObject.getSignature());
            streamFileSignature.setFilename(signatureFileData.getStreamFilename());
            streamFileSignature.setMetadataHash(metadataHashObject.getHash());
            streamFileSignature.setMetadataHashSignature(metadataHashSignatureObject.getSignature());
            streamFileSignature.setSignatureType(fileHashSignatureObject.getSignatureType());
            streamFileSignature.setVersion(VERSION);

            return streamFileSignature;
        } catch (InvalidStreamFileException | IOException e) {
            throw new SignatureFileParsingException(e);
        }
    }

    @EqualsAndHashCode(callSuper = true)
    @Getter
    private static class SignatureObject extends AbstractStreamObject {

        private final byte[] signature;
        private final SignatureType signatureType;

        SignatureObject(ValidatedDataInputStream vdis, String sectionName) {
            super(vdis);

            try {
                signatureType = SignatureType.SHA_384_WITH_RSA;
                vdis.readInt(signatureType.getFileMarker(), sectionName, "signature type");
                signature = vdis.readLengthAndBytes(1, signatureType.getMaxLength(), true, sectionName, "signature");
            } catch (IOException e) {
                throw new InvalidStreamFileException(e);
            }
        }
    }
}
