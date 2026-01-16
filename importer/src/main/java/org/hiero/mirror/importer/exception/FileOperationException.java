// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.exception;

@SuppressWarnings("java:S110")
public class FileOperationException extends ImporterException {

    private static final long serialVersionUID = 5194246300993814767L;

    public FileOperationException(String message) {
        super(message);
    }

    public FileOperationException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
