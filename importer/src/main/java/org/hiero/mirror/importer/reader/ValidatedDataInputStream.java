// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;

/**
 * A validated data input stream extends {@link DataInputStream} by providing methods to validate several types
 * of value read from the stream with expected value.
 */
public class ValidatedDataInputStream extends DataInputStream {

    private static final String NOT_EQUAL_ERROR_MESSAGE = "Unable to read %s: Expected %s but got %s";
    private static final String NOT_IN_RANGE_ERROR_MESSAGE =
            "Unable to read %s: " + "Expected value between %d and %d but got %d";
    private static final int SIMPLE_SUM = 101;

    private final String resourceName;

    /**
     * Creates a ValidatedDataInputStream that uses the specified underlying {@link InputStream}.
     *
     * @param in the specified input stream
     * @param resourceName the name of the resource {@code in} is created from
     */
    public ValidatedDataInputStream(InputStream in, String resourceName) {
        super(in);
        this.resourceName = resourceName;
    }

    public byte readByte(byte expected, String fieldName) throws IOException {
        return readByte(expected, null, fieldName);
    }

    public byte readByte(byte expected, String sectionName, String fieldName) throws IOException {
        byte actual = super.readByte();
        return validate(expected, actual, sectionName, fieldName);
    }

    public int readInt(int expected, String fieldName) throws IOException {
        return readInt(expected, null, fieldName);
    }

    public int readInt(int expected, String sectionName, String fieldName) throws IOException {
        int actual = super.readInt();
        return validate(expected, actual, sectionName, fieldName);
    }

    public byte[] readLengthAndBytes(int minLength, int maxLength, boolean hasChecksum, String type)
            throws IOException {
        return readLengthAndBytes(minLength, maxLength, hasChecksum, null, type);
    }

    public byte[] readLengthAndBytes(int minLength, int maxLength, boolean hasChecksum, String sectionName, String type)
            throws IOException {
        String typeLength = type + " length";

        int length = super.readInt();
        if (minLength == maxLength) {
            validate(minLength, length, sectionName, typeLength);
        } else {
            validateBetween(minLength, maxLength, length, sectionName, typeLength);
        }

        if (hasChecksum) {
            int checksum = super.readInt();
            validate(SIMPLE_SUM - length, checksum, sectionName, "checksum");
        }

        return readNBytes(length, sectionName, "actual " + typeLength);
    }

    public byte[] readNBytes(int expectedLength, String fieldName) throws IOException {
        return readNBytes(expectedLength, null, fieldName);
    }

    public byte[] readNBytes(int expectedLength, String sectionName, String fieldName) throws IOException {
        byte[] bytes = super.readNBytes(expectedLength);
        validate(expectedLength, bytes.length, sectionName, fieldName);
        return bytes;
    }

    private <T> T validate(T expected, T actual, String sectionName, String fieldName) {
        if (!Objects.equals(expected, actual)) {
            throw new InvalidStreamFileException(
                    String.format(NOT_EQUAL_ERROR_MESSAGE, getFullFieldName(sectionName, fieldName), expected, actual));
        }

        return actual;
    }

    private void validateBetween(
            int minimumExpected, int maximumExpected, int actual, String sectionName, String fieldName) {
        if (actual < minimumExpected || actual > maximumExpected) {
            throw new InvalidStreamFileException(String.format(
                    NOT_IN_RANGE_ERROR_MESSAGE,
                    getFullFieldName(sectionName, fieldName),
                    minimumExpected,
                    maximumExpected,
                    actual));
        }
    }

    private String getFullFieldName(String sectionName, String fieldName) {
        List<String> parts = Arrays.asList(resourceName, "field", sectionName, fieldName).stream()
                .filter(StringUtils::isNotEmpty)
                .toList();
        if (parts.size() == 1) {
            return "";
        }

        return StringUtils.join(parts, ' ');
    }
}
