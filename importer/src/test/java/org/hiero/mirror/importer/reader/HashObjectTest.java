// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.DigestAlgorithm.SHA_384;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HashObjectTest {

    @ParameterizedTest
    @CsvSource({
        "1226, 1, , 0, false",
        "1228, 1, , 0, false",
        "1226, 2, , 0, false",
        "1226, 1, 2, 0, true",
        "1226, 1, , 1, true",
        "1226, 1, , 49, true",
        "1226, 1, , 53, true",
        "1226, 1, , 57, true",
        "1226, 1, , 61, true",
    })
    void newHashObject(long classId, int classVersion, Integer digestType, int bytesToTruncate, boolean expectThrown)
            throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(bos)) {
            // given
            dos.writeLong(classId);
            dos.writeInt(classVersion);
            digestType = Objects.requireNonNullElseGet(digestType, SHA_384::getType);
            dos.writeInt(digestType);
            dos.writeInt(SHA_384.getSize());
            byte[] hash = TestUtils.generateRandomByteArray(SHA_384.getSize());
            dos.write(hash);

            byte[] data = bos.toByteArray();
            if (bytesToTruncate > 0) {
                data = Arrays.copyOfRange(data, 0, data.length - bytesToTruncate);
            }

            try (ValidatedDataInputStream dis = new ValidatedDataInputStream(new ByteArrayInputStream(data), "test")) {
                // when, then
                if (expectThrown) {
                    assertThrows(InvalidStreamFileException.class, () -> new HashObject(dis, SHA_384));
                } else {
                    HashObject expected = new HashObject(classId, classVersion, SHA_384.getType(), hash);
                    HashObject actual = new HashObject(dis, "testfile", SHA_384);
                    assertThat(actual).isEqualTo(expected);
                }
            }
        }
    }
}
