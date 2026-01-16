// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.signature;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.security.SecureRandom;
import lombok.SneakyThrows;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.exception.SignatureFileParsingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompositeSignatureFileReaderTest {

    private static final String SIGNATURE_FILENAME = "2021-03-10T16_30_00Z.rcd_sig";

    @Mock
    private SignatureFileReaderV2 signatureFileReaderV2;

    @Mock
    private SignatureFileReaderV5 signatureFileReaderV5;

    @Mock
    private ProtoSignatureFileReader protoSignatureFileReader;

    private CompositeSignatureFileReader compositeBalanceFileReader;

    @BeforeEach
    void setUp() {
        compositeBalanceFileReader = new CompositeSignatureFileReader(
                signatureFileReaderV2, signatureFileReaderV5, protoSignatureFileReader);
    }

    @Test
    void testValidV2() {
        var signatureFileBytes = getSignatureFileBytes(SignatureFileReaderV2.SIGNATURE_TYPE_FILE_HASH);
        var streamFileData = StreamFileData.from(SIGNATURE_FILENAME, signatureFileBytes);
        compositeBalanceFileReader.read(streamFileData);
        verify(signatureFileReaderV2, times(1)).read(any(StreamFileData.class));
        verify(signatureFileReaderV5, never()).read(any(StreamFileData.class));
        verify(protoSignatureFileReader, never()).read(any(StreamFileData.class));
    }

    @Test
    void testValidV5() {
        var signatureFileBytes = getSignatureFileBytes(SignatureFileReaderV5.VERSION);
        var streamFileData = StreamFileData.from(SIGNATURE_FILENAME, signatureFileBytes);
        compositeBalanceFileReader.read(streamFileData);
        verify(signatureFileReaderV5, times(1)).read(any(StreamFileData.class));
        verify(signatureFileReaderV2, never()).read(any(StreamFileData.class));
        verify(protoSignatureFileReader, never()).read(any(StreamFileData.class));
    }

    @Test
    void testValidV6() {
        var signatureFileBytes = getSignatureFileBytes(ProtoSignatureFileReader.VERSION);
        var streamFileData = StreamFileData.from(SIGNATURE_FILENAME, signatureFileBytes);
        compositeBalanceFileReader.read(streamFileData);
        verify(signatureFileReaderV5, never()).read(any(StreamFileData.class));
        verify(signatureFileReaderV2, never()).read(any(StreamFileData.class));
        verify(protoSignatureFileReader, times(1)).read(any(StreamFileData.class));
    }

    @Test
    void testBlankFile() {
        var blankFileData = StreamFileData.from(SIGNATURE_FILENAME, new byte[0]);
        var exception = assertThrows(SignatureFileParsingException.class, () -> {
            compositeBalanceFileReader.read(blankFileData);
        });
        assertAll(
                () -> assertTrue(exception.getMessage().contains("Error reading signature file")),
                () -> assertTrue(exception.getCause() instanceof IOException));
    }

    @Test
    void testInvalidFileVersion() {
        byte invalidVersionNumber = 12;
        var signatureFileBytes = getSignatureFileBytes(invalidVersionNumber);
        var invalidFileData = StreamFileData.from(SIGNATURE_FILENAME, signatureFileBytes);
        var exception = assertThrows(
                SignatureFileParsingException.class, () -> compositeBalanceFileReader.read(invalidFileData));
        assertTrue(exception.getMessage().contains("Unsupported signature file version: " + invalidVersionNumber));
    }

    @SneakyThrows
    private byte[] getSignatureFileBytes(int version) {
        byte[] bytes = new byte[4];
        SecureRandom.getInstanceStrong().nextBytes(bytes);
        bytes[0] = (byte) version;
        return bytes;
    }
}
