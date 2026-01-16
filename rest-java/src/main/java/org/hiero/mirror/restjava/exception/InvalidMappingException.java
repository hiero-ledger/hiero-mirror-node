// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.exception;

import java.io.Serial;

@SuppressWarnings("java:S110")
public class InvalidMappingException extends RestJavaException {

    @Serial
    private static final long serialVersionUID = -857679581991526245L;

    public InvalidMappingException(String message, Throwable cause) {
        super(message, cause);
    }
}
