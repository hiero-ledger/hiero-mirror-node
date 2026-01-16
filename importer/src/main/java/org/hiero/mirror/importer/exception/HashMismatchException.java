// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.exception;

import java.io.Serial;
import org.hiero.mirror.common.util.DomainUtils;

@SuppressWarnings("java:S110")
public class HashMismatchException extends ImporterException {

    private static final String MESSAGE = "%s hash mismatch for file %s. Expected = %s, Actual = %s";

    @Serial
    private static final long serialVersionUID = -1093315700008851731L;

    public HashMismatchException(String filename, String expectedHash, String actualHash, String hashType) {
        super(String.format(MESSAGE, hashType, filename, expectedHash, actualHash));
    }

    public HashMismatchException(String filename, byte[] expectedHash, byte[] actualHash, String hashType) {
        super(String.format(
                MESSAGE, hashType, filename, DomainUtils.bytesToHex(expectedHash), DomainUtils.bytesToHex(actualHash)));
    }
}
