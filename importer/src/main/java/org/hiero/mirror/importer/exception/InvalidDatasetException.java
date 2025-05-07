// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.exception;

/**
 * Invalid dataset such as an account balances dataset.
 */
@SuppressWarnings("java:S110")
public class InvalidDatasetException extends ImporterException {

    private static final long serialVersionUID = 3679395824341309905L;

    public InvalidDatasetException(String message) {
        super(message);
    }

    public InvalidDatasetException(Throwable throwable) {
        super(throwable);
    }

    public InvalidDatasetException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
