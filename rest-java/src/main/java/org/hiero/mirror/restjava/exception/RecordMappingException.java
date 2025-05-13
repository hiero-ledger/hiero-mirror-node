// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.exception;

import java.io.Serial;

@SuppressWarnings("java:S110")
public class RecordMappingException extends RestJavaException {

    @Serial
    private static final long serialVersionUID = 4044997636814538880L;

    public RecordMappingException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
