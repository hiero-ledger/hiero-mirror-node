// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.exception;

@SuppressWarnings("java:S110")
public class StreamFileReaderException extends ImporterException {

    private static final long serialVersionUID = 2533328395713171797L;

    public StreamFileReaderException(Throwable throwable) {
        super(throwable);
    }

    public StreamFileReaderException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
