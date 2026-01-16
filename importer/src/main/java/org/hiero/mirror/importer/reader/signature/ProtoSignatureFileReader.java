// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.signature;

import static java.lang.String.format;

import com.hedera.services.stream.proto.SignatureFile;
import jakarta.inject.Named;
import java.io.DataInputStream;
import java.io.IOException;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.domain.StreamFileSignature;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;

@Named
public class ProtoSignatureFileReader implements SignatureFileReader {

    public static final byte VERSION = 6;

    @Override
    public StreamFileSignature read(StreamFileData signatureFileData) {
        var filename = signatureFileData.getFilename();
        try {
            var signatureFile = readSignatureFile(signatureFileData);

            var fileSignature = signatureFile.getFileSignature();
            var metadataSignature = signatureFile.getMetadataSignature();

            var streamFileSignature = new StreamFileSignature();
            streamFileSignature.setBytes(signatureFileData.getBytes());
            streamFileSignature.setFileHash(DomainUtils.getHashBytes(fileSignature.getHashObject()));
            streamFileSignature.setFileHashSignature(DomainUtils.toBytes(fileSignature.getSignature()));
            streamFileSignature.setFilename(signatureFileData.getStreamFilename());
            streamFileSignature.setMetadataHash(DomainUtils.getHashBytes(metadataSignature.getHashObject()));
            streamFileSignature.setMetadataHashSignature(DomainUtils.toBytes(metadataSignature.getSignature()));
            streamFileSignature.setSignatureType(StreamFileSignature.SignatureType.valueOf(
                    fileSignature.getType().toString()));
            streamFileSignature.setVersion(VERSION);

            return streamFileSignature;
        } catch (IllegalArgumentException | IOException e) {
            throw new InvalidStreamFileException(filename, e);
        }
    }

    private SignatureFile readSignatureFile(StreamFileData signatureFileData) throws IOException {
        try (var dataInputStream = new DataInputStream(signatureFileData.getInputStream())) {
            var filename = signatureFileData.getFilename();
            byte version = dataInputStream.readByte();
            if (version != VERSION) {
                var message = format("Expected file %s with version %d, got %d", filename, VERSION, version);
                throw new InvalidStreamFileException(message);
            }

            var signatureFile = SignatureFile.parseFrom(dataInputStream);
            if (!signatureFile.hasFileSignature()) {
                throw new InvalidStreamFileException(format("The file %s does not have a file signature", filename));
            }

            if (!signatureFile.hasMetadataSignature()) {
                var message = format("The file %s does not have a file metadata signature", filename);
                throw new InvalidStreamFileException(message);
            }

            return signatureFile;
        }
    }
}
