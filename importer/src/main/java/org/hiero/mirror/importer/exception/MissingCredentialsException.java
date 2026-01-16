// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.exception;

@SuppressWarnings("java:S110")
public class MissingCredentialsException extends ImporterException {

    private static final long serialVersionUID = 121078402562575433L;

    public MissingCredentialsException(String message) {
        super(message);
    }
}
