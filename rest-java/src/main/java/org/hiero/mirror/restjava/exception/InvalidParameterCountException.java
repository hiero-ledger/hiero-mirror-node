// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.exception;

import java.io.Serial;

/**
 * Exception thrown when a request parameter is invalid. This exception's message is returned directly to the client as
 * the error message.
 */
@SuppressWarnings("java:S110")
public class InvalidParameterCountException extends RestJavaException {

    @Serial
    private static final long serialVersionUID = 1234567890123456789L;

    public InvalidParameterCountException(String message) {
        super(message);
    }

    public InvalidParameterCountException(String message, Throwable cause) {
        super(message, cause);
    }
}
