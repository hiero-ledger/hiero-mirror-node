// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.exception;

@SuppressWarnings("java:S110")
public class SignatureVerificationException extends ImporterException {

    private static final long serialVersionUID = 4830495870121480440L;

    public SignatureVerificationException(String message) {
        super(message);
    }
}
