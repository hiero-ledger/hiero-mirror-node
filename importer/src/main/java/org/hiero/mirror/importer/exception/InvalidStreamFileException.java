// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.exception;

@SuppressWarnings("java:S110")
public class InvalidStreamFileException extends ImporterException {

    private static final long serialVersionUID = 2786469980314810323L;

    public InvalidStreamFileException(String message) {
        super(message);
    }

    public InvalidStreamFileException(Throwable throwable) {
        super(throwable);
    }

    public InvalidStreamFileException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
