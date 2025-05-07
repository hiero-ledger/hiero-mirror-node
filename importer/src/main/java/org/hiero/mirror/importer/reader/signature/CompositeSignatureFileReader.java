// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.signature;

import jakarta.inject.Named;
import java.io.DataInputStream;
import java.io.IOException;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.domain.StreamFileSignature;
import org.hiero.mirror.importer.exception.SignatureFileParsingException;
import org.springframework.context.annotation.Primary;

@CustomLog
@Named
@Primary
@RequiredArgsConstructor
public class CompositeSignatureFileReader implements SignatureFileReader {

    private final SignatureFileReaderV2 signatureFileReaderV2;
    private final SignatureFileReaderV5 signatureFileReaderV5;
    private final ProtoSignatureFileReader protoSignatureFileReader;

    @Override
    public StreamFileSignature read(StreamFileData signatureFileData) {
        try (DataInputStream dataInputStream = new DataInputStream(signatureFileData.getInputStream())) {
            byte version = dataInputStream.readByte();
            SignatureFileReader fileReader;

            if (version == SignatureFileReaderV5.VERSION) {
                fileReader = signatureFileReaderV5;
            } else if (version <= SignatureFileReaderV2.SIGNATURE_TYPE_FILE_HASH) { // Begins with a byte of value 4
                fileReader = signatureFileReaderV2;
            } else if (version == ProtoSignatureFileReader.VERSION) {
                fileReader = protoSignatureFileReader;
            } else {
                throw new SignatureFileParsingException("Unsupported signature file version: " + version);
            }

            return fileReader.read(signatureFileData);
        } catch (IOException ex) {
            throw new SignatureFileParsingException("Error reading signature file", ex);
        }
    }
}
