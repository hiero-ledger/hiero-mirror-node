// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.google.common.primitives.Ints;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ValidatedDataInputStreamTest {

    @ParameterizedTest
    @CsvSource({"0, 0", "0, 1"})
    void readByte(byte actual, byte expected) throws IOException {
        try (ValidatedDataInputStream dis = getValidatedDataInputStream(new byte[] {actual})) {
            if (actual == expected) {
                assertThat(dis.readByte(expected, "testfield")).isEqualTo(expected);
            } else {
                assertThrows(InvalidStreamFileException.class, () -> dis.readByte(expected, "testfield"));
            }
        }
    }

    @Test
    void readByteTruncated() throws IOException {
        try (ValidatedDataInputStream dis = getValidatedDataInputStream(new byte[0])) {
            assertThrows(IOException.class, () -> dis.readInt(0, "testfield"));
        }
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "0, 1"})
    void readInt(int actual, int expected) throws IOException {
        try (ValidatedDataInputStream dis = getValidatedDataInputStream(Ints.toByteArray(actual))) {
            if (actual == expected) {
                assertThat(dis.readInt(expected, "testfield")).isEqualTo(expected);
            } else {
                assertThrows(InvalidStreamFileException.class, () -> dis.readInt(expected, "testfield"));
            }
        }
    }

    @Test
    void readIntTruncated() throws IOException {
        try (ValidatedDataInputStream dis = getValidatedDataInputStream(new byte[Integer.BYTES - 1])) {
            assertThrows(IOException.class, () -> dis.readInt(0, "testfield"));
        }
    }

    @ParameterizedTest(name = "read length({0}) data from {1}-byte value field with valid length in [{3}, {4}]")
    @CsvSource({
        "1, 2, 1, 10, false",
        "6, 6, 1, 10, false",
        "6, 10, 1, 10, false",
        "6, 6, 6, 6, false",
        "6, 6, 7, 10, true",
        "5, 6, 6, 6, true",
        "6, 5, 1, 10, true",
        "0, 2, 1, 10, true",
        "-1, 2, 1, 10, true",
        "11, 10, 1, 10, true"
    })
    void readLengthAndBytes(int length, int dataLength, int minLength, int maxLength, boolean expectThrown)
            throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(bos)) {
            dos.writeInt(length);
            dos.write(new byte[dataLength]);
            byte[] data = bos.toByteArray();

            try (var dis = new ValidatedDataInputStream(new ByteArrayInputStream(data), "testfile")) {
                if (expectThrown) {
                    assertThrows(
                            InvalidStreamFileException.class,
                            () -> dis.readLengthAndBytes(minLength, maxLength, false, "test"));
                } else {
                    byte[] expected = new byte[length];
                    byte[] actual = dis.readLengthAndBytes(minLength, maxLength, false, "test");

                    assertArrayEquals(actual, expected);
                }
            }
        }
    }

    @ParameterizedTest(name = "read length({0}) data with checksum {1}, expectThrown ? {2}")
    @CsvSource({"101, 0, false", "101, 100, true"})
    void readLengthAndBytesWithChecksum(int length, int checksum, boolean expectThrown) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(bos)) {
            dos.writeInt(length);
            dos.writeInt(checksum);
            dos.write(new byte[length]);
            byte[] data = bos.toByteArray();

            try (var dis = new ValidatedDataInputStream(new ByteArrayInputStream(data), "testfile")) {
                if (expectThrown) {
                    assertThrows(
                            InvalidStreamFileException.class,
                            () -> dis.readLengthAndBytes(length, length, true, "test"));
                } else {
                    byte[] expected = new byte[length];
                    byte[] actual = dis.readLengthAndBytes(length, length, true, "test");

                    assertArrayEquals(actual, expected);
                }
            }
        }
    }

    @Test
    void readLengthAndBytesWithTruncatedLengthField() throws IOException {
        try (ValidatedDataInputStream dis = getValidatedDataInputStream(new byte[1])) {
            assertThrows(IOException.class, () -> dis.readLengthAndBytes(10, 10, false, "testfield"));
        }
    }

    @ParameterizedTest
    @CsvSource({"10, 10", "12, 10", "6, 10"})
    void readNBytes(int actualLength, int expectedLength) throws IOException {
        try (ValidatedDataInputStream dis = getValidatedDataInputStream(new byte[actualLength])) {
            if (actualLength >= expectedLength) {
                byte[] expected = new byte[expectedLength];
                byte[] actual = dis.readNBytes(expectedLength, "testfield");

                assertArrayEquals(actual, expected);
            } else {
                assertThrows(InvalidStreamFileException.class, () -> dis.readNBytes(expectedLength, "testfield"));
            }
        }
    }

    private ValidatedDataInputStream getValidatedDataInputStream(byte[] data) {
        return new ValidatedDataInputStream(new BufferedInputStream(new ByteArrayInputStream(data)), "testfile");
    }
}
