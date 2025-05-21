// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.List;
import org.hiero.mirror.common.domain.DigestAlgorithm;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.domain.StreamFileSignature;
import org.hiero.mirror.importer.domain.StreamFileSignature.SignatureType;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class SignatureFileReaderV5Test extends AbstractSignatureFileReaderTest {

    private static final long HASH_CLASS_ID = 0xf422da83a251741eL;
    private static final int HASH_CLASS_VERSION = 1;
    private static final long SIGNATURE_CLASS_ID = 0x13dc4b399b245c69L;
    private static final int SIGNATURE_CLASS_VERSION = 1;
    private static final SignatureType signatureType = SignatureType.SHA_384_WITH_RSA;
    private static final byte VERSION = 5;

    private static final String ENTIRE_FILE_HASH_BASE_64 =
            "L+OAVq+qeyicnL+lVSL5XIBy8JSYGaTVGa9ADG59s" + "+ZOUHcTaAHR3KxX0Cooc5Jo";
    private static final String ENTIRE_FILE_SIGNATURE_BASE_64 = "LN5tEHPqE6VRQPGXSWWEG1LOquVtOgHRu5LFGNCLrH7"
            + "/cCd4xbC1RnOeQ+E9czLwdZaHWxQsc80ooD62oRLcw33XFMZGgVvXRWvG9lltUP0TRfDSRNAxz"
            + "+31CAnvVa9ds7Q3eKGUHskB7rwIeJRQXVz6UHonA7WAmypeRbKFJ3x7AdLef8EaH6X83Bs3vmX5LnUWVQ"
            + "/m8vPNwC089hheWhbkRIZlwJ1lN6Jy+rQev/5kAGGhxUgfQls4nQwNq4OjleVQTChwLKi0nHVhdbEouoMOBS"
            + "YW2tZhgP0iLcWK0eg1AQF10cAwevdlxiY1WXU21L76KZOmTYYo5Esxke++ntA2XiWzOmcBGud"
            + "/4xDq1LvssNlKSKKrMYMsdcS4nTz3AkdSAMLxkIq7hB+ZC12RcODEaqyglnNawWHY4ril7L1lo0Bt"
            + "/TtFQsK7JcBGVPBqb5MO5DtSWfFAkxpOwFSOc63gyyqZwYx6ieC+mgFToLjxh/7rbG1+spLN4YhfMosr";

    private static final String METADATA_HASH_BASE_64 =
            "yySh8+IzClpzk6Q/TQK18D33jqVNN6iZmZqd5p9QN21tUs2ESCGeim32ANIfoapi";
    private static final String METADATA_SIGNATURE_BASE_64 = "g6xHyKexXgyFWLxcnnQ/8H7efoswLAWRS9T0KGLBSdUVV0cu9d+cQr3"
            + "/8gwrTb7yGN/7fvHrNoGtXDlEK9TiBHeqn6iVG/EIkJqpBZLUReGrZRwDPMO+stj1aDlEOl143jOesMiMNYsl"
            + "+27uerYfXLrkrdW3dlznCYu16frBMugQKuH2oTck2cT0AZmtHqLFkKKsjBWnxlmx+z2dlp"
            + "/LhIg4ajM0ZZmYU7GBL8akE4iIljwOVbrQpHmvhhNS6uCuUW0qAg/JIoR8a6fXRki4USyRCrv"
            + "+2z1HXjsMM497WSfIvHugvLFOII3GUCMeVjKWPeXM7UZ6lqa6jyd+uXhmgOaBnzGfOCcfwalGejgeBiphkQBNVdiZ"
            + "+xFHwmKhAvsKXyp2ZFIrB+PGMQI8wr1cCMYLKYpI4VceCkLTIB3XOOVKZPWZaOs8MK9Aj9ZeT3REqf"
            + "d252N19j2yA45x8Zs2kRIC2iKNNEPwcaUbGNHiPmsZ5Ezq0lnNKuomJECMsYHu";

    private final Decoder base64Codec = Base64.getDecoder();
    private final SignatureFileReaderV5 fileReaderV5 = new SignatureFileReaderV5();
    private final File signatureFile =
            TestUtils.getResource(Path.of("data", "signature", "v5", "2021-01-11T22_16_11.299356001Z.rcd_sig")
                    .toString());

    @Test
    void testReadValidFile() {
        StreamFileData streamFileData = StreamFileData.from(signatureFile);
        StreamFileSignature streamFileSignature = fileReaderV5.read(streamFileData);

        assertNotNull(streamFileSignature);
        assertThat(streamFileSignature.getBytes()).isNotEmpty().isEqualTo(streamFileData.getBytes());
        assertArrayEquals(base64Codec.decode(ENTIRE_FILE_HASH_BASE_64), streamFileSignature.getFileHash());
        assertArrayEquals(
                base64Codec.decode(ENTIRE_FILE_SIGNATURE_BASE_64), streamFileSignature.getFileHashSignature());
        assertArrayEquals(base64Codec.decode(METADATA_HASH_BASE_64), streamFileSignature.getMetadataHash());
        assertArrayEquals(
                base64Codec.decode(METADATA_SIGNATURE_BASE_64), streamFileSignature.getMetadataHashSignature());
        assertEquals(VERSION, streamFileSignature.getVersion());
    }

    @SuppressWarnings("java:S2699")
    @TestFactory
    Iterable<DynamicTest> testReadCorruptSignatureFileV5() {

        SignatureFileSection fileVersion = new SignatureFileSection(
                new byte[] {VERSION}, "invalidFileFormatVersion", incrementLastByte, "fileVersion");

        SignatureFileSection objectStreamSignatureVersion = new SignatureFileSection(
                Ints.toByteArray(SignatureType.SHA_384_WITH_RSA.getFileMarker()), null, null, null);

        List<SignatureFileSection> signatureFileSections = new ArrayList<>();
        signatureFileSections.add(fileVersion);
        signatureFileSections.add(objectStreamSignatureVersion);

        signatureFileSections.addAll(buildHashSections("entireFile"));
        signatureFileSections.addAll(buildSignatureSections("entireFile"));
        signatureFileSections.addAll(buildHashSections("metadata"));
        signatureFileSections.addAll(buildSignatureSections("metadata"));

        SignatureFileSection invalidExtraData = new SignatureFileSection(
                new byte[0], "invalidExtraData", bytes -> new byte[] {1}, "Extra data discovered in signature file");

        signatureFileSections.add(invalidExtraData);

        return generateCorruptedFileTests(fileReaderV5, signatureFileSections);
    }

    private List<SignatureFileSection> buildHashSections(String sectionName) {
        SignatureFileSection hashClassId = new SignatureFileSection(Longs.toByteArray(HASH_CLASS_ID), null, null, null);

        SignatureFileSection hashClassVersion =
                new SignatureFileSection(Ints.toByteArray(HASH_CLASS_VERSION), null, null, null);

        SignatureFileSection hashDigestType = new SignatureFileSection(
                Ints.toByteArray(DigestAlgorithm.SHA_384.getType()),
                "invalidHashDigestType:" + sectionName,
                incrementLastByte,
                sectionName + " hash digest type");

        SignatureFileSection hashLength = new SignatureFileSection(
                Ints.toByteArray(DigestAlgorithm.SHA_384.getSize()),
                "invalidHashLength:" + sectionName,
                incrementLastByte,
                "hash length");

        SignatureFileSection hash = new SignatureFileSection(
                TestUtils.generateRandomByteArray(DigestAlgorithm.SHA_384.getSize()),
                "incorrectHashLength:" + sectionName,
                truncateLastByte,
                sectionName + " actual hash length");
        return Arrays.asList(hashClassId, hashClassVersion, hashDigestType, hashLength, hash);
    }

    private List<SignatureFileSection> buildSignatureSections(String sectionName) {
        SignatureFileSection signatureClassId =
                new SignatureFileSection(Longs.toByteArray(SIGNATURE_CLASS_ID), null, null, null);

        SignatureFileSection signatureClassVersion =
                new SignatureFileSection(Ints.toByteArray(SIGNATURE_CLASS_VERSION), null, null, null);

        SignatureFileSection signatureFileMarker = new SignatureFileSection(
                Ints.toByteArray(signatureType.getFileMarker()),
                "invalidSignatureType:" + sectionName,
                incrementLastByte,
                sectionName + " signature type");

        SignatureFileSection signatureLength = new SignatureFileSection(
                Ints.toByteArray(signatureType.getMaxLength()),
                "signatureLengthTooLong",
                incrementLastByte,
                sectionName + " signature length");

        SignatureFileSection checkSum = new SignatureFileSection(
                Ints.toByteArray(101 - signatureType.getMaxLength()),
                "incorrectCheckSum:" + sectionName,
                incrementLastByte,
                sectionName + " checksum");

        SignatureFileSection signature = new SignatureFileSection(
                TestUtils.generateRandomByteArray(signatureType.getMaxLength()),
                "incorrectSignatureLength:" + sectionName,
                truncateLastByte,
                sectionName + " actual signature length");

        return Arrays.asList(
                signatureClassId, signatureClassVersion, signatureFileMarker, signatureLength, checkSum, signature);
    }
}
