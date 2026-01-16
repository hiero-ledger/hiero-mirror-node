// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.exception;

/**
 * Invalid ethereum transaction bytes encountered during decode.
 */
@SuppressWarnings("java:S110")
public class InvalidEthereumBytesException extends InvalidDatasetException {

    private static final long serialVersionUID = -3253044226905756499L;

    private static final String DECODE_ERROR_PREFIX_MESSAGE = "Unable to decode %s ethereum transaction bytes, %s";

    public InvalidEthereumBytesException(String ethereumTransactionType, String message) {
        super(String.format(DECODE_ERROR_PREFIX_MESSAGE, ethereumTransactionType, message));
    }
}
