// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.exception;

import java.io.Serial;

@SuppressWarnings("java:S110")
public class EntityNotFoundException extends RestJavaException {

    @Serial
    private static final long serialVersionUID = -6913223815380771123L;

    public EntityNotFoundException(String message) {
        super(message);
    }

    public EntityNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
