// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.exception;

@SuppressWarnings("java:S110")
public class SignatureFileParsingException extends ImporterException {
    private static final long serialVersionUID = 8172331078550974122L;

    public SignatureFileParsingException(String message) {
        super(message);
    }

    public SignatureFileParsingException(Throwable throwable) {
        super(throwable);
    }

    public SignatureFileParsingException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
