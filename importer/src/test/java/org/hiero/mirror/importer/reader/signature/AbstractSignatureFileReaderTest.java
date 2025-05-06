// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.google.common.primitives.Bytes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.Value;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.exception.SignatureFileParsingException;
import org.junit.jupiter.api.DynamicTest;

abstract class AbstractSignatureFileReaderTest {

    private static final String SIGNATURE_FILENAME = "2021-03-10T16_30_00Z.rcd_sig";

    // Dynamically generate tests for corrupt/invalid signature file tests
    protected Iterable<DynamicTest> generateCorruptedFileTests(
            SignatureFileReader fileReader, List<SignatureFileSection> signatureFileSections) {
        List<DynamicTest> testCases = new ArrayList<>();

        // Add a test for an empty stream
        testCases.add(DynamicTest.dynamicTest("blankFile", () -> {
            StreamFileData blankFileData = StreamFileData.from(SIGNATURE_FILENAME, new byte[0]);
            SignatureFileParsingException e =
                    assertThrows(SignatureFileParsingException.class, () -> fileReader.read(blankFileData));
            assertTrue(e.getMessage().contains("EOFException"));
        }));

        byte[] validSignatureBytes = new byte[0];
        for (int i = 0; i < signatureFileSections.size(); i++) {
            // Add new valid section of the signature file to the array
            validSignatureBytes = i > 0
                    ? Bytes.concat(
                            validSignatureBytes,
                            signatureFileSections.get(i - 1).getValidDataBytes())
                    : validSignatureBytes;

            SignatureFileSection sectionToCorrupt = signatureFileSections.get(i);

            // Some sections are not validated by the reader and don't need a test
            if (sectionToCorrupt.getCorruptTestName() == null) {
                continue;
            }

            // Add the corrupted section of the signature file to the valid sections
            byte[] fullSignatureBytes = Bytes.concat(validSignatureBytes, sectionToCorrupt.getCorruptBytes());

            // Create a test that checks that an exception was thrown, and the message matches.
            testCases.add(DynamicTest.dynamicTest(signatureFileSections.get(i).getCorruptTestName(), () -> {
                StreamFileData corruptedFileData = StreamFileData.from(SIGNATURE_FILENAME, fullSignatureBytes);
                SignatureFileParsingException e =
                        assertThrows(SignatureFileParsingException.class, () -> fileReader.read(corruptedFileData));
                sectionToCorrupt.validateError(e.getMessage());
            }));
        }
        return testCases;
    }

    protected static final SignatureFileSectionCorrupter incrementLastByte = (bytes -> {
        byte[] corruptBytes = Arrays.copyOf(bytes, bytes.length);
        corruptBytes[corruptBytes.length - 1] = (byte) (corruptBytes[corruptBytes.length - 1] + 1);
        return corruptBytes;
    });

    protected static final SignatureFileSectionCorrupter truncateLastByte =
            (bytes -> Arrays.copyOfRange(bytes, 0, bytes.length - 1));

    @Value
    protected class SignatureFileSection {
        byte[] validDataBytes;
        String corruptTestName;
        SignatureFileSectionCorrupter byteCorrupter;
        String invalidExceptionMessage;

        @Getter(lazy = true)
        byte[] corruptBytes = byteCorrupter.corruptBytes(validDataBytes);

        public void validateError(String errorMessage) {
            assertThat(errorMessage).contains(invalidExceptionMessage);
        }
    }

    protected interface SignatureFileSectionCorrupter {
        byte[] corruptBytes(byte[] bytes);
    }
}
