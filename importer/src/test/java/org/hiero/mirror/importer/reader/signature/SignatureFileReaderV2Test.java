// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.reader.signature.SignatureFileReaderV2.SIGNATURE_TYPE_FILE_HASH;
import static org.hiero.mirror.importer.reader.signature.SignatureFileReaderV2.SIGNATURE_TYPE_SIGNATURE;
import static org.junit.jupiter.api.Assertions.*;

import com.google.common.primitives.Ints;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.codec.binary.Base64;
import org.hiero.mirror.common.domain.DigestAlgorithm;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.domain.StreamFileSignature;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class SignatureFileReaderV2Test extends AbstractSignatureFileReaderTest {

    private static final String ENTIRE_FILE_HASH_BASE_64 =
            "WRVY4Fm9FinuOGxONaaHW0xnoJZxj10iV3KmUQQnFRiUFN99tViEle" + "+yqF3EoP/a";
    private static final String ENTIRE_FILE_SIGNATURE_BASE_64 = "nOVITUEb1WfYLJN4Jp2/aIEYTiqEzfTSMU5Y6KDKbCi55"
            + "+vsWasqfQaUE4JLGC+JO+Ky2Ui1WsnDHCDxxE/Jx0K+90n2eg8pFZLlA6xcMZ4fLchy6+mhQWYhtRSdCr6aO0JV4lOtFUSZ"
            + "/DC4qIiwo0VaHNkWCw+bhrERFKeTZcxzHtiElGEeggxwFMvNXBUigU2LoWWLm5BDS9N35iRrfEf6g0HybYe2tOiA717vlKvIMr0t"
            + "YJmlLLKUB9brEUpdSm8RRLs+jzEY76YT7Uv6WzIq04SetI+GUOMkEXDNvtcSKnE8625L7qmhbiiX4Ub90jCxCqt6JHXrCM1VsYWEn"
            + "/oUesRi5pnATgjqZOXycMegavb1Ikf3GoQAvn1Bx6EO14Uh7hVMxa/NYMtSVNQ17QG6QtA4j7viVvJ9EPSiCsmg3Cp2PhBW5ZPshq"
            + "+ExciGbnXFu+ytLZGSwKhePwuLQsBNTbGUcDFy1IJge95tEweR51Y1Nfh6PqPTnkdirRGO";
    private static final int SIGNATURE_LENGTH = 48;
    private static final byte VERSION = 2;

    private final SignatureFileReaderV2 fileReaderV2 = new SignatureFileReaderV2();
    private final File signatureFile =
            TestUtils.getResource(Path.of("data", "signature", "v2", "2019-08-30T18_10_00.419072Z.rcd_sig")
                    .toString());

    @Test
    void testReadValidFile() {
        StreamFileData streamFileData = StreamFileData.from(signatureFile);
        StreamFileSignature streamFileSignature = fileReaderV2.read(streamFileData);
        assertNotNull(streamFileSignature);
        assertThat(streamFileSignature.getBytes()).isNotEmpty().isEqualTo(streamFileData.getBytes());
        assertArrayEquals(Base64.decodeBase64(ENTIRE_FILE_HASH_BASE_64.getBytes()), streamFileSignature.getFileHash());
        assertArrayEquals(
                Base64.decodeBase64(ENTIRE_FILE_SIGNATURE_BASE_64.getBytes()),
                streamFileSignature.getFileHashSignature());
        assertEquals(VERSION, streamFileSignature.getVersion());
    }

    @SuppressWarnings("java:S2699")
    @TestFactory
    Iterable<DynamicTest> testReadCorruptSignatureFileV2() {
        SignatureFileSection hashDelimiter = new SignatureFileSection(
                new byte[] {SIGNATURE_TYPE_FILE_HASH}, "invalidHashDelimiter", incrementLastByte, "hash delimiter");

        SignatureFileSection hash = new SignatureFileSection(
                TestUtils.generateRandomByteArray(DigestAlgorithm.SHA_384.getSize()),
                "invalidHashLength",
                truncateLastByte,
                "hash");

        SignatureFileSection signatureDelimiter = new SignatureFileSection(
                new byte[] {SIGNATURE_TYPE_SIGNATURE},
                "invalidSignatureDelimiter",
                incrementLastByte,
                "signature delimiter");

        SignatureFileSection signatureLength =
                new SignatureFileSection(Ints.toByteArray(SIGNATURE_LENGTH), null, null, null);

        SignatureFileSection signature = new SignatureFileSection(
                TestUtils.generateRandomByteArray(SIGNATURE_LENGTH),
                "incorrectSignatureLength",
                truncateLastByte,
                "actual signature length");

        SignatureFileSection invalidExtraData = new SignatureFileSection(
                new byte[0], "invalidExtraData", bytes -> new byte[] {1}, "Extra data discovered in signature file");

        List<SignatureFileSection> signatureFileSections =
                Arrays.asList(hashDelimiter, hash, signatureDelimiter, signatureLength, signature, invalidExtraData);

        return generateCorruptedFileTests(fileReaderV2, signatureFileSections);
    }
}
