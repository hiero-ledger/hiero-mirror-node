// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.exception;

@SuppressWarnings("java:S110")
public class ParserException extends ImporterException {

    private static final long serialVersionUID = 5216154273755649844L;

    public ParserException(String message) {
        super(message);
    }

    public ParserException(Throwable throwable) {
        super(throwable);
    }

    public ParserException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
